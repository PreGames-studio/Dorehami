package ir.dorehami.games.backgammon

import ir.dorehami.gamesdk.ApplyResult
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * One backgammon match: players[0] is white (moves 24 → 1), players[1] is black.
 *
 * A turn is two actions at most: `roll`, then one `play` carrying the **whole** maximal sequence of
 * die-uses. Sequences are validated against [BackgammonRules.sequences], so "use as many dice as you
 * can" and "the higher die wins when only one can be played" are enforced by the rules, not by the
 * screen. When a player cannot move at all the turn passes inside the accepted `roll`.
 *
 * Match play: a finished game scores 1 (single), 2 (gammon) or 3 (backgammon); the first player to
 * reach `targetPoints` wins, otherwise the board resets and the game winner rolls first.
 */
class BackgammonSession(private val config: GameConfig) : GameSession {

    object Options {
        const val TARGET_POINTS = "targetPoints"
    }

    private val whitePlayer: PlayerId = config.players[0]
    private val blackPlayer: PlayerId = config.players[1]
    private val targetPoints: Int =
        (config.options[Options.TARGET_POINTS] as? JsonPrimitive)?.intOrNull ?: BackgammonModule.DEFAULT_TARGET_POINTS

    private var position: BackgammonPosition = BackgammonPosition()
    private var dice: List<Int> = emptyList()
    private var whiteToMove: Boolean = true
    private var scoreWhite: Int = 0
    private var scoreBlack: Int = 0
    private var gameNumber: Int = 1
    private var lastMoves: List<BackgammonMove> = emptyList()
    private var lastHit: Boolean = false
    private var finished: MatchResult? = null

    override var phase: Phase = Phase.PLAYING
        private set
    override var version: Long = 0L
        private set

    init {
        require(config.players.size == 2) { "backgammon is a two player game" }
    }

    // ---- contract ----

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        if (phase == Phase.FINISHED) return rejected(ErrCodes.BAD_STATE, "این مسابقه تمام شده است")
        val isWhite = player == whitePlayer
        if (player != whitePlayer && player != blackPlayer) {
            return rejected(ErrCodes.UNKNOWN_PLAYER, "این بازیکن در این بازی نیست")
        }
        if (isWhite != whiteToMove) return rejected(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")

        return when (action.type) {
            BackgammonModule.ACTION_ROLL -> roll(isWhite)
            BackgammonModule.ACTION_PLAY -> play(isWhite, action.payload)
            else -> rejected(ErrCodes.ILLEGAL_ACTION, "این حرکت در تخته نرد وجود ندارد")
        }
    }

    private fun roll(isWhite: Boolean): ApplyResult {
        if (dice.isNotEmpty()) return rejected(ErrCodes.ILLEGAL_ACTION, "تاس همین حالا ریخته شده است")
        val first = 1 + config.rng.nextInt(6)
        val second = 1 + config.rng.nextInt(6)
        val rolled = if (first == second) List(4) { first } else listOf(first, second)
        dice = rolled
        lastMoves = emptyList()
        lastHit = false
        version++

        val emissions = mutableListOf<Emission>(
            Emission.Feed(
                key = "backgammon.roll",
                args = buildJsonObject {
                    put("white", isWhite)
                    put("first", first)
                    put("second", second)
                },
                kind = "info",
            ),
            Emission.Sound("dice"),
        )

        // No legal die-use at all: the turn passes (the standard rule), nothing else to submit.
        if (BackgammonRules.sequences(position, rolled, isWhite).all { it.isEmpty() }) {
            passTurn(isWhite)
            emissions += Emission.Feed(
                key = "backgammon.no_move",
                args = buildJsonObject { put("white", isWhite) },
                kind = "warn",
            )
            emissions += Emission.Sound("pass")
        }
        return ApplyResult.Ok(emissions)
    }

    private fun play(isWhite: Boolean, payload: JsonObject): ApplyResult {
        if (dice.isEmpty()) return rejected(ErrCodes.BAD_STATE, "اول تاس بریز")
        val requested = parseMoves(payload)
            ?: return rejected(ErrCodes.BAD_PAYLOAD, "فهرست حرکات خوانده نشد")

        // Walk the sequence once: every die must still be available and every step legal on the
        // board as it stands after the previous steps.
        var current = position
        var hit = false
        val remaining = dice.toMutableList()
        for (move in requested) {
            val slot = remaining.indexOf(move.die)
            if (slot < 0) return rejected(ErrCodes.ILLEGAL_ACTION, "این تاس در دسترس نیست")
            if (!BackgammonRules.isLegal(current, move, isWhite)) {
                return rejected(ErrCodes.ILLEGAL_ACTION, "این حرکت با مهره‌های روی تخته نمی‌خواند")
            }
            remaining.removeAt(slot)
            val target = BackgammonRules.destination(move, isWhite)
            if (target != BackgammonRules.TO_OFF && BackgammonRules.rivalCount(current, target, isWhite) == 1) {
                hit = true
            }
            current = BackgammonRules.apply(current, move, isWhite)
        }

        val allowed = BackgammonRules.sequences(position, dice, isWhite)
        if (allowed.none { it == requested }) {
            return rejected(
                ErrCodes.ILLEGAL_ACTION,
                if (allowed.all { it.isEmpty() }) "حرکتی برای این تاس‌ها نیست" else "باید بیشترین استفاده از تاس‌ها بشود",
            )
        }

        position = current
        lastMoves = requested
        lastHit = hit
        dice = emptyList()
        version++

        val emissions = mutableListOf<Emission>(
            Emission.Feed(
                key = "backgammon.play",
                args = buildJsonObject {
                    put("white", isWhite)
                    put("count", requested.size)
                    put("hit", hit)
                },
                kind = "info",
            ),
            Emission.Sound(if (hit) "hit" else "checker"),
        )

        val winnerWhite = position.whiteFinished
        val winnerBlack = position.blackFinished
        if (winnerWhite || winnerBlack) {
            emissions += settleGame(winnerIsWhite = winnerWhite)
        } else {
            passTurn(isWhite)
        }
        return ApplyResult.Ok(emissions)
    }

    private fun settleGame(winnerIsWhite: Boolean): List<Emission> {
        val value = BackgammonRules.gameValue(position, loserIsWhite = !winnerIsWhite)
        if (winnerIsWhite) scoreWhite += value else scoreBlack += value
        val matchOver = scoreWhite >= targetPoints || scoreBlack >= targetPoints
        val emissions = mutableListOf<Emission>(
            Emission.Feed(
                key = if (value == 3) "backgammon.backgammon" else if (value == 2) "backgammon.gammon" else "backgammon.single",
                args = buildJsonObject {
                    put("white", winnerIsWhite)
                    put("value", value)
                    put("whiteScore", scoreWhite)
                    put("blackScore", scoreBlack)
                },
                kind = "info",
            ),
            Emission.Sound("win"),
        )
        if (matchOver) {
            phase = Phase.FINISHED
            finished = if (scoreWhite >= targetPoints) {
                MatchResult(listOf(Standing(listOf(whitePlayer), 1, scoreWhite), Standing(listOf(blackPlayer), 2, scoreBlack)))
            } else {
                MatchResult(listOf(Standing(listOf(blackPlayer), 1, scoreBlack), Standing(listOf(whitePlayer), 2, scoreWhite)))
            }
            emissions += Emission.Feed(
                key = "backgammon.match_over",
                args = buildJsonObject {
                    put("white", scoreWhite >= targetPoints)
                    put("whiteScore", scoreWhite)
                    put("blackScore", scoreBlack)
                },
                kind = "info",
            )
        } else {
            // Next game: fresh board, the game winner rolls first.
            position = BackgammonPosition()
            dice = emptyList()
            gameNumber++
            whiteToMove = winnerIsWhite
            lastMoves = emptyList()
            lastHit = false
        }
        return emissions
    }

    private fun passTurn(isWhite: Boolean) {
        dice = emptyList()
        whiteToMove = !isWhite
    }

    private fun parseMoves(payload: JsonObject): List<BackgammonMove>? {
        val raw = payload[BackgammonModule.PAYLOAD_MOVES] as? JsonArray ?: return null
        val moves = ArrayList<BackgammonMove>(raw.size)
        for (element in raw) {
            val entry = element as? JsonObject ?: return null
            val from = (entry["from"] as? JsonPrimitive)?.intOrNull ?: return null
            val die = (entry["die"] as? JsonPrimitive)?.intOrNull ?: return null
            moves += BackgammonMove.parse(from, die) ?: return null
        }
        return moves
    }

    override fun publicView(): JsonObject = view {
        put(
            "points",
            buildJsonArray { position.points.forEach { add(JsonPrimitive(it)) } },
        )
        put(
            "bar",
            buildJsonObject {
                put("white", position.barWhite)
                put("black", position.barBlack)
            },
        )
        put(
            "off",
            buildJsonObject {
                put("white", position.offWhite)
                put("black", position.offBlack)
            },
        )
        put("turn", (if (whiteToMove) whitePlayer else blackPlayer).value)
        put("white", whitePlayer.value)
        put("black", blackPlayer.value)
        put(
            "dice",
            buildJsonArray { dice.forEach { add(JsonPrimitive(it)) } },
        )
        put("mustRoll", dice.isEmpty())
        put("game", gameNumber)
        put("target", targetPoints)
        put(
            "score",
            buildJsonObject {
                put("white", scoreWhite)
                put("black", scoreBlack)
            },
        )
        put(
            "last",
            buildJsonObject {
                put("hit", lastHit)
                put(
                    "moves",
                    buildJsonArray {
                        lastMoves.forEach { move ->
                            add(
                                buildJsonObject {
                                    put("from", move.from)
                                    put("die", move.die)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    override fun privateView(player: PlayerId): JsonObject = view {
        publicView().forEach { (key, value) -> put(key, value) }
        put("you", player.value)
        put(
            "actions",
            buildJsonArray { legalActions(player).forEach { add(JsonPrimitive(it)) } },
        )
    }

    override fun legalActions(player: PlayerId): List<String> {
        if (phase == Phase.FINISHED) return emptyList()
        val isWhite = player == whitePlayer
        if (player != whitePlayer && player != blackPlayer) return emptyList()
        if (isWhite != whiteToMove) return emptyList()
        return listOf(if (dice.isEmpty()) BackgammonModule.ACTION_ROLL else BackgammonModule.ACTION_PLAY)
    }

    override fun result(): MatchResult? = finished

    override fun snapshot(): JsonObject = view {
        publicView().forEach { (key, value) -> put(key, value) }
        put("version", version)
        put("phase", phase.name)
        put("rng", config.rng.state)
        put("seed", config.seed)
    }

    // ---- helpers used by the bot and the UI ----

    /** Current board; the bot only ever sees public information (the whole board is public here). */
    fun board(): BackgammonPosition = position

    fun currentDice(): List<Int> = dice

    fun isWhiteTurn(): Boolean = whiteToMove

    /** Maximal sequences available right now (used by the bot and the table's move hints). */
    fun availableSequences(): List<List<BackgammonMove>> =
        if (dice.isEmpty()) emptyList() else BackgammonRules.sequences(position, dice, whiteToMove)

    fun playerColor(player: PlayerId): Boolean? = when (player) {
        whitePlayer -> true
        blackPlayer -> false
        else -> null
    }

    private fun rejected(code: String, messageFa: String) = ApplyResult.Rejected(code, messageFa)
}
