package ir.dorehami.games.esmfamil

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.EMPTY_JSON
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Standing
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * One match of اسم فامیل: a letter is drawn, everyone fills the same sheet, the sheets are opened
 * and scored, then the next letter comes.
 *
 * The session is a pure function of `(seed, action log)` — exactly like every other game in the
 * repo (AGENTS.md rule 2). It never reads a clock: the round is closed either when the last player
 * submits or when somebody sends [EsmFamilModule.ACTION_FINISH_ROUND] (the phone that owns the
 * table does that when its own timer runs out or when a seat is empty). Time is therefore part of
 * the action log, and a replay reproduces the same sheets and the same points.
 *
 * Hidden information: during [RoundPhase.WRITING] the public view carries **no** answer of anybody;
 * a player only ever sees their own row, through [privateView]. Everything becomes public at
 * [RoundPhase.REVEAL] because that is the moment the table reads the sheets out loud.
 */
class EsmFamilSession(private val config: GameConfig) : GameSession {

    /** Room options (`GameConfig.options`), validated by [EsmFamilModule.validateOptions]. */
    object Options {
        const val ROUNDS = "rounds"
        const val CATEGORIES = "categories"
        const val LETTERS = "letters"
    }

    /** Keys of the views. The UI reads these; nothing else may invent keys. */
    object Keys {
        const val ROUND = "round"
        const val ROUNDS = "rounds"
        const val LETTER = "letter"
        const val PHASE = "phase"
        const val CATEGORIES = "categories"
        const val SUBMITTED = "submitted"
        const val SCORES = "scores"
        const val SHEET = "sheet"
        const val ROUND_POINTS = "roundPoints"
        const val MINE = "mine"
        const val SEAT = "seat"
        const val STANDINGS = "standings"
        const val FINISHED = "finished"
    }

    /** Where the round is. `FINISHED` is the session phase, not a round phase. */
    enum class RoundPhase { WRITING, REVEAL }

    private companion object {
        /** Longer than any sane اسم for a party game; protects the wire from a paste accident. */
        const val MAX_ANSWER_CHARS = 40
    }

    private val players: List<PlayerId> = config.players
    private val rounds: Int = (config.options[Options.ROUNDS] as? JsonPrimitive)?.intOrNull
        ?: EsmFamilModule.DEFAULT_ROUNDS
    private val categoryIds: List<String> = EsmFamilModule.categoriesOf(config.options)
    private val letters: List<Char> = EsmFamilSheet.letters(
        (config.options[Options.LETTERS] as? JsonPrimitive)?.contentOrNull ?: EsmFamilSheet.LETTERS_COMMON,
    )

    private val totals = LinkedHashMap<PlayerId, Int>().apply { players.forEach { put(it, 0) } }
    private var round: Int = 1
    private var letter: Char = drawLetter(null)
    private var roundPhase: RoundPhase = RoundPhase.WRITING
    private val sheet = LinkedHashMap<PlayerId, Map<String, String>>()
    private val submitted = LinkedHashSet<PlayerId>()
    private var roundPoints: Map<PlayerId, Map<String, Int>> = emptyMap()
    private var finished: Boolean = false
    private var table: MatchResult? = null

    override var version: Long = 0L
        private set

    override val phase: Phase get() = if (finished) Phase.FINISHED else Phase.PLAYING

    // ------------------------------------------------------------------ rules

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        if (player !in players) {
            return ApplyResult.Rejected(ErrCodes.UNKNOWN_PLAYER, "این بازیکن پشت این میز نیست")
        }
        if (finished) {
            return ApplyResult.Rejected(ErrCodes.BAD_STATE, "این دست تمام شده است")
        }
        return when (action.type) {
            EsmFamilModule.ACTION_SUBMIT -> submit(player, action)
            EsmFamilModule.ACTION_FINISH_ROUND -> finishRound()
            EsmFamilModule.ACTION_NEXT_ROUND -> nextRound()
            else -> ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "این حرکت را نمی‌شناسم")
        }
    }

    /** One player hands in their sheet. The last sheet closes the round. */
    private fun submit(player: PlayerId, action: GameAction): ApplyResult {
        if (roundPhase != RoundPhase.WRITING) {
            return ApplyResult.Rejected(ErrCodes.BAD_STATE, "برگه‌های این دور بسته شده است")
        }
        if (player in submitted) {
            return ApplyResult.Rejected(ErrCodes.BAD_STATE, "برگهٔ این دور را قبلاً دادی")
        }
        val raw = action.payload[EsmFamilModule.PAYLOAD_ANSWERS] as? JsonObject
            ?: return ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "برگه خالی فرستاده شد")
        val row = LinkedHashMap<String, String>()
        for (category in categoryIds) {
            val text = (raw[category] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (text.length > MAX_ANSWER_CHARS) {
                return ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "پاسخ در ستون «${EsmFamilSheet.titleOf(category)}» بلندتر از حد است")
            }
            row[category] = text
        }
        sheet[player] = row
        submitted += player
        version++
        val emissions = mutableListOf<Emission>(
            Emission.Feed(
                "esmfamil_sheet_in",
                view {
                    put("player", player.value)
                    put("done", submitted.size)
                    put("total", players.size)
                },
            ),
            Emission.Sound("esmfamil_sheet"),
        )
        if (submitted.size == players.size) emissions += closeRound()
        return ApplyResult.Ok(emissions)
    }

    /**
     * The table closes the round without waiting any longer (timer over, or a seat went quiet).
     * Whoever holds the table sends this; the session only checks that a round is actually open.
     */
    private fun finishRound(): ApplyResult {
        if (roundPhase != RoundPhase.WRITING) {
            return ApplyResult.Rejected(ErrCodes.BAD_STATE, "این دور از قبل بسته شده است")
        }
        version++
        return ApplyResult.Ok(closeRound())
    }

    /** Open sheets are public; scores are added; the table is now in [RoundPhase.REVEAL]. */
    private fun closeRound(): List<Emission> {
        roundPoints = EsmFamilSheet.scoreRound(letter, categoryIds, sheet)
        for (player in players) {
            totals[player] = (totals[player] ?: 0) + (roundPoints[player]?.values?.sum() ?: 0)
        }
        roundPhase = RoundPhase.REVEAL
        return listOf(
            Emission.Public("esmfamil.reveal", view { publicFields() }),
            Emission.Feed(
                "esmfamil_revealed",
                view {
                    put(Keys.ROUND, round)
                    put(Keys.LETTER, letter.toString())
                },
            ),
            Emission.Sound("esmfamil_reveal"),
        )
    }

    /** Deal the next letter, or finish the match after the last round. */
    private fun nextRound(): ApplyResult {
        if (roundPhase != RoundPhase.REVEAL) {
            return ApplyResult.Rejected(ErrCodes.BAD_STATE, "اول باید برگه‌های این دور رو شوند")
        }
        version++
        if (round >= rounds) {
            finished = true
            table = standings()
            return ApplyResult.Ok(
                listOf(
                    Emission.Public("esmfamil.finished", view { publicFields() }),
                    Emission.Feed("esmfamil_finished", EMPTY_JSON),
                    Emission.Sound("esmfamil_win"),
                ),
            )
        }
        round++
        letter = drawLetter(letter)
        roundPhase = RoundPhase.WRITING
        sheet.clear()
        submitted.clear()
        roundPoints = emptyMap()
        return ApplyResult.Ok(
            listOf(
                Emission.Public(
                    "esmfamil.round",
                    view {
                        put(Keys.ROUND, round)
                        put(Keys.LETTER, letter.toString())
                        put(Keys.ROUNDS, rounds)
                    },
                ),
                Emission.Feed(
                    "esmfamil_round",
                    view {
                        put(Keys.ROUND, round)
                        put(Keys.LETTER, letter.toString())
                    },
                ),
                Emission.Sound("esmfamil_round"),
            ),
        )
    }

    /** Draws from the injected [GameConfig.rng], so the same seed deals the same letters. */
    private fun drawLetter(previous: Char?): Char {
        if (letters.isEmpty()) return 'ا'
        val index = config.rng.nextInt(letters.size)
        val picked = letters[index]
        // A letter never repeats back to back: the table would think the app froze.
        return if (previous != null && picked == previous) letters[(index + 1) % letters.size] else picked
    }

    // ------------------------------------------------------------------ views

    override fun publicView(): JsonObject = buildJsonObject { publicFields() }

    private fun kotlinx.serialization.json.JsonObjectBuilder.publicFields() {
        put(Keys.ROUND, round)
        put(Keys.ROUNDS, rounds)
        put(Keys.LETTER, letter.toString())
        put(Keys.PHASE, if (finished) "FINISHED" else roundPhase.name)
        put(Keys.FINISHED, finished)
        put(Keys.CATEGORIES, buildJsonArray { categoryIds.forEach { add(JsonPrimitive(it)) } })
        put(Keys.SUBMITTED, buildJsonArray { submitted.forEach { add(JsonPrimitive(it.value)) } })
        put(Keys.SCORES, totalsJson())
        // Answers and points exist publicly only after the sheets are opened.
        if (roundPhase == RoundPhase.REVEAL) {
            put(Keys.SHEET, sheetJson())
            put(Keys.ROUND_POINTS, pointsJson())
        }
        table?.let { put(Keys.STANDINGS, standingsJson(it)) }
    }

    override fun privateView(player: PlayerId): JsonObject = buildJsonObject {
        publicFields()
        put(Keys.SEAT, players.indexOf(player))
        put(Keys.MINE, rowJson(sheet[player].orEmpty()))
    }

    private fun totalsJson(): JsonObject = buildJsonObject {
        players.forEach { put(it.value, totals[it] ?: 0) }
    }

    private fun sheetJson(): JsonObject = buildJsonObject {
        players.forEach { put(it.value, rowJson(sheet[it].orEmpty())) }
    }

    private fun rowJson(row: Map<String, String>): JsonObject = buildJsonObject {
        categoryIds.forEach { put(it, row[it].orEmpty()) }
    }

    private fun pointsJson(): JsonObject = buildJsonObject {
        players.forEach { player ->
            val per = roundPoints[player].orEmpty()
            put(
                player.value,
                buildJsonObject { categoryIds.forEach { put(it, per[it] ?: 0) } },
            )
        }
    }

    private fun standingsJson(result: MatchResult): JsonObject = buildJsonObject {
        put(
            "table",
            buildJsonArray {
                result.standings.forEach { standing ->
                    add(
                        view {
                            put("player", standing.members.firstOrNull()?.value ?: "")
                            put("rank", standing.rank)
                            put("score", standing.score)
                        },
                    )
                }
            },
        )
    }

    override fun legalActions(player: PlayerId): List<String> = when {
        finished -> emptyList()
        roundPhase == RoundPhase.WRITING -> buildList {
            if (player !in submitted) add(EsmFamilModule.ACTION_SUBMIT)
            add(EsmFamilModule.ACTION_FINISH_ROUND)
        }
        else -> listOf(EsmFamilModule.ACTION_NEXT_ROUND)
    }

    override fun result(): MatchResult? = table

    override fun snapshot(): JsonObject = buildJsonObject {
        publicFields()
        put(Keys.SHEET, sheetJson())
        put(Keys.ROUND_POINTS, pointsJson())
    }

    // ------------------------------------------------------------------ table

    private fun standings(): MatchResult {
        val ordered = players.sortedByDescending { totals[it] ?: 0 }
        val standings = ArrayList<Standing>(ordered.size)
        var rank = 0
        var previousScore: Int? = null
        ordered.forEachIndexed { index, player ->
            val score = totals[player] ?: 0
            if (score != previousScore) {
                rank = index + 1
                previousScore = score
            }
            standings += Standing(members = listOf(player), rank = rank, score = score)
        }
        return MatchResult(standings)
    }
}
