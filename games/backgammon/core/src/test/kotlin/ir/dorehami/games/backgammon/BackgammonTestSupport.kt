package ir.dorehami.games.backgammon

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Rng
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Shared helpers for the backgammon contract tests. No network, no sleeps, no mocks frameworks. */
internal object BackgammonTestSupport {

    val white = PlayerId("p_white")
    val black = PlayerId("p_black")

    /** Rng that hands out the dice a test asked for (values are 1..6). */
    class ScriptedRng(private vararg val dice: Int) : Rng {
        private var index = 0
        override var state: Long = 0L

        override fun nextInt(bound: Int): Int {
            require(bound == 6) { "backgammon only asks for d6" }
            val value = dice[index % dice.size]
            index++
            return value - 1
        }

        override fun nextLong(): Long = nextInt(6).toLong()
    }

    fun config(
        seed: Long = 1L,
        target: Int = 5,
        rng: Rng = SplitMixRng(seed),
    ) = GameConfig(
        seed = seed,
        players = listOf(white, black),
        options = view { put(BackgammonSession.Options.TARGET_POINTS, target) },
        clock = ManualClock(),
        rng = rng,
    )

    fun session(seed: Long = 1L, target: Int = 5, rng: Rng = SplitMixRng(seed)): BackgammonSession =
        BackgammonModule.create(config(seed, target, rng)) as BackgammonSession

    fun engine(seed: Long = 1L, target: Int = 5, rng: Rng = SplitMixRng(seed)): GameEngine =
        GameEngine.start(BackgammonModule, config(seed, target, rng))

    fun roll(): GameAction = GameAction(BackgammonModule.ACTION_ROLL)

    fun play(vararg moves: Pair<Int, Int>): GameAction = GameAction(
        type = BackgammonModule.ACTION_PLAY,
        payload = buildJsonObject {
            put(
                BackgammonModule.PAYLOAD_MOVES,
                buildJsonArray {
                    moves.forEach { (from, die) ->
                        add(
                            buildJsonObject {
                                put("from", from)
                                put("die", die)
                            },
                        )
                    }
                },
            )
        },
    )

    /**
     * A board built from `index to count` entries; positive is white, negative is black.
     * Missing entries are empty points.
     */
    fun board(
        vararg entries: Pair<Int, Int>,
        barWhite: Int = 0,
        barBlack: Int = 0,
        offWhite: Int = 0,
        offBlack: Int = 0,
    ): BackgammonPosition {
        val points = IntArray(BackgammonRules.POINT_COUNT)
        entries.forEach { (index, count) -> points[index] = count }
        return BackgammonPosition(points.toList(), barWhite, barBlack, offWhite, offBlack)
    }

    fun ok(result: ApplyResult): ApplyResult.Ok {
        check(result is ApplyResult.Ok) { "expected Ok, got $result" }
        return result
    }

    /** Plays a whole match with the deterministic bot on both seats. */
    fun playWithBots(engine: GameEngine, maxActions: Int = 40_000): List<Emission> {
        val emitted = ArrayList<Emission>()
        var guard = 0
        while (engine.session.phase != Phase.FINISHED) {
            check(guard++ < maxActions) { "bot match did not finish in $maxActions actions" }
            val session = engine.session as BackgammonSession
            val actor = if (session.isWhiteTurn()) white else black
            when (val result = engine.submitBot(actor)) {
                is ApplyResult.Ok -> emitted += result.emissions
                is ApplyResult.Rejected -> error("bot action rejected: ${result.code} — ${result.messageFa}")
                null -> error("bot had no action for $actor")
            }
        }
        return emitted
    }
}
