package ir.dorehami.games.esmfamil

import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/** Shared helpers for the اسم فامیل contract tests: no Android, no sleeps, no mock framework. */
internal object EsmFamilTestSupport {

    val sara = PlayerId("p_sara")
    val reza = PlayerId("p_reza")
    val mina = PlayerId("p_mina")

    fun config(
        players: List<PlayerId> = listOf(sara, reza, mina),
        seed: Long = 7L,
        rounds: Int = 3,
        categories: List<String> = EsmFamilSheet.DEFAULT_IDS,
        letters: String = EsmFamilSheet.LETTERS_COMMON,
    ) = GameConfig(
        seed = seed,
        players = players,
        options = view {
            put(EsmFamilSession.Options.ROUNDS, rounds)
            put(
                EsmFamilSession.Options.CATEGORIES,
                buildJsonArray { categories.forEach { add(JsonPrimitive(it)) } },
            )
            put(EsmFamilSession.Options.LETTERS, letters)
        },
        clock = ManualClock(),
        rng = SplitMixRng(seed),
    )

    fun session(
        players: List<PlayerId> = listOf(sara, reza, mina),
        seed: Long = 7L,
        rounds: Int = 3,
    ): EsmFamilSession = EsmFamilModule.create(config(players, seed, rounds)) as EsmFamilSession

    fun engine(
        players: List<PlayerId> = listOf(sara, reza, mina),
        seed: Long = 7L,
        rounds: Int = 3,
    ): GameEngine = GameEngine.start(EsmFamilModule, config(players, seed, rounds))

    /** A sheet for the current letter: every column gets the same word, which is fine for tests. */
    fun sheet(letter: Char, word: String): GameAction = GameAction(
        type = EsmFamilModule.ACTION_SUBMIT,
        payload = view {
            put(
                EsmFamilModule.PAYLOAD_ANSWERS,
                view { EsmFamilSheet.DEFAULT_IDS.forEach { put(it, "$letter$word") } },
            )
        },
    )

    /** A hand-written sheet: category id → answer (missing categories are sent empty). */
    fun sheetOf(vararg answers: Pair<String, String>): GameAction = GameAction(
        type = EsmFamilModule.ACTION_SUBMIT,
        payload = view {
            put(
                EsmFamilModule.PAYLOAD_ANSWERS,
                view {
                    EsmFamilSheet.DEFAULT_IDS.forEach { category ->
                        put(category, answers.firstOrNull { it.first == category }?.second.orEmpty())
                    }
                },
            )
        },
    )

    fun finishRound(): GameAction = GameAction(EsmFamilModule.ACTION_FINISH_ROUND)

    fun nextRound(): GameAction = GameAction(EsmFamilModule.ACTION_NEXT_ROUND)

    fun letterOf(session: EsmFamilSession): Char =
        session.publicView()["letter"]!!.let { (it as JsonPrimitive).content }.first()
}
