package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HK-013 — the *fixed baseline scenario*: one deterministic match, generated from a fixed seed
 * through the real rules and the real bot policy, captured after every accepted action as the
 * typed table state of **every** seat.
 *
 * Why this exists: a quality baseline is worthless if each measurement watches a different game.
 * Frame timing, memory growth, TalkBack traversal order, font-scale layout and (later) golden
 * screenshots must all be taken on *the same* sequence of table states, so that numbers from
 * different devices, builds and dates are comparable. This object is that sequence — produced by
 * code, not hand-written, so it cannot drift from the rules: change the rules and the scenario
 * fingerprint changes, which is exactly what a baseline must report.
 *
 * It is pure JVM (no Compose, no Android) so it runs in `checkAll` and in the local
 * `tools/jvm-check` runner; a device harness reads the same states through the app's own table.
 */
object HokmBaselineScenario {

    /**
     * The primary reference match: four players, default options, one fixed seed, the shipped bot
     * policy moving for every non-human seat. This is *the* scenario named in BASELINE.md.
     */
    const val SEED = 20261014L

    /** The secondary scenario: the two-player variant (stock, keep/pass, burning). */
    const val SEED_2P = 777L

    /** One accepted action plus the whole table as every seat sees it right after it. */
    data class Step(
        val index: Int,
        val actorSeat: Int,
        val actionType: String,
        val engineVersion: Long,
        val states: List<TableSnapshot>,
    ) {
        /** The actor's own view of the table after their move. */
        val state: TableSnapshot get() = states[actorSeat]
    }

    /** The generated scenario, with the fingerprint that pins it. */
    data class Scenario(
        val playerCount: Int,
        val seed: Long,
        val steps: List<Step>,
        val finalDigest: String,
        val fingerprint: String,
    ) {
        /** True once the scripted match actually reached its result. */
        val finished: Boolean get() = steps.lastOrNull()?.states?.firstOrNull()?.finished == true

        /** Every hand phase that occurs somewhere in the scenario. */
        val phases: Set<HandPhase> get() = steps.flatMap { s -> s.states.map { it.handPhase } }.toSet()

        /** Hands played to decide the match. */
        val hands: Int get() = steps.last().states.first().handNumber

        /** Fingerprint of the state sequence only (independent of the action log). */
        fun stateDigest(): String = GameEngine.sha256(statesKey())

        private fun statesKey(): String = buildString {
            for (s in steps) {
                append(s.index).append(':').append(s.actorSeat).append(':')
                for (v in s.states) append(v.toString()).append(';')
            }
        }
    }

    private val SEAT_IDS = listOf(PlayerId("p0"), PlayerId("p1"), PlayerId("p2"), PlayerId("p3"))

    /**
     * Generates the scenario. Deterministic by construction: the seed fixes the deal, the bot
     * policy is a pure function of the session (ADR-0003), and the clock never moves on its own —
     * so two runs of this function produce byte-identical state sequences.
     *
     * @param maxActions a hard stop so a rules change can never hang the build.
     */
    fun generate(
        playerCount: Int = 4,
        seed: Long = SEED,
        botLevel: String = HokmModule.BOT_PRO,
        maxActions: Int = 4000,
    ): Scenario {
        val players = SEAT_IDS.take(playerCount)
        val options = buildJsonObject {
            // Default options, normalised by the module itself, with the scenario's bot level.
            HokmModule.validateOptions(HokmModule.defaultOptions()).forEach { (k, v) -> put(k, v) }
            put(HokmSession.Options.BOT_LEVEL, botLevel)
        }
        val engine = GameEngine.start(
            HokmModule,
            GameConfig(
                seed = seed,
                players = players,
                options = options,
                clock = ManualClock(),
                rng = SplitMixRng(seed),
            ),
        )

        val steps = ArrayList<Step>()
        var guard = 0
        while (engine.session.result() == null && guard++ < maxActions) {
            val actor = (0 until playerCount).firstOrNull { engine.session.legalActions(players[it]).isNotEmpty() }
                ?: error("baseline scenario: no seat has a legal action at step ${steps.size} but the match is not over")
            // The *shipped* bot policy moves for every seat, through the official engine loop —
            // the baseline measures the product's own play, not a test-only shortcut.
            val r = engine.submitBot(players[actor])
                ?: error("baseline scenario: the bot policy had no move for seat $actor at step ${steps.size}")
            check(r is ApplyResult.Ok) { "baseline scenario: the bot's own move was rejected at step ${steps.size}" }
            val logged = engine.actions.last()
            check(logged.player == players[actor]) { "baseline scenario: the log does not match the actor" }
            steps += capture(engine, steps.size, actor, logged.type, playerCount, players)
        }
        check(engine.session.result() != null) {
            "baseline scenario did not finish within $maxActions actions — a rules change broke the fixed scenario"
        }

        val record = engine.record()
        return Scenario(
            playerCount = playerCount,
            seed = seed,
            steps = steps,
            finalDigest = record.digest,
            fingerprint = GameEngine.sha256(
                buildString {
                    append(playerCount).append('|').append(seed).append('|').append(botLevel).append('|')
                    append(record.actions.size).append('|').append(record.digest).append('|')
                    for (s in steps) {
                        append(s.index).append(':').append(s.actorSeat).append(':').append(s.actionType).append(';')
                    }
                    for (s in steps) {
                        for (v in s.states) append(v.toString()).append(';')
                    }
                },
            ),
        )
    }

    /** The four-player reference scenario. Computed lazily once; tests share it. */
    val fourPlayer: Scenario by lazy { generate() }

    /** The two-player variant scenario (stock / keep-pass / burning). */
    val twoPlayer: Scenario by lazy { generate(playerCount = 2, seed = SEED_2P) }

    // ------------------------------------------------------------------ internals

    private fun capture(
        engine: GameEngine,
        index: Int,
        actorSeat: Int,
        actionType: String,
        playerCount: Int,
        players: List<PlayerId>,
    ): Step {
        val states = ArrayList<TableSnapshot>(playerCount)
        for (seat in 0 until playerCount) {
            // The real wire payload of that seat, through the real typed mapper (HK-009).
            val view = engine.session.privateView(players[seat])
            val mapped = HokmUiMapper.map(view)
            check(mapped is MappedView.Valid) {
                val rejected = mapped as MappedView.Rejected
                "baseline scenario: the real view of seat $seat was refused at step $index " +
                    "(${rejected.code}: ${rejected.messageFa}) — the scenario must stay inside the supported contract"
            }
            check(mapped.snapshot.mySeat == seat) {
                "baseline scenario: seat $seat got a view for seat ${mapped.snapshot.mySeat}"
            }
            states += mapped.snapshot
        }
        return Step(index, actorSeat, actionType, engine.session.version, states)
    }

    /**
     * The move the scenario makes for [seat] is always the shipped bot policy's move, applied
     * through [GameEngine.submitBot] in [generate] — there is deliberately no second, test-only
     * move generator that could drift from the product.
     */

    /** The largest hand any seat holds during the scenario (13 for a four-player deal). */
    fun maxHandSize(scenario: Scenario): Int =
        scenario.steps.maxOf { s -> s.states.maxOf { it.myHand.size } }

    /** Seats that act at least once in the scenario. */
    fun actingSeats(scenario: Scenario): Set<Int> = scenario.steps.map { it.actorSeat }.toSet()

    /** Action types that occur in the scenario, in order of first appearance. */
    fun actionTypes(scenario: Scenario): List<String> =
        scenario.steps.map { it.actionType }.distinct()

    /** The first step whose actor holds a full [Card] hand of [size], if any. */
    fun firstStepWithHand(scenario: Scenario, size: Int): Step? =
        scenario.steps.firstOrNull { s -> s.state.myHand.size == size }
}
