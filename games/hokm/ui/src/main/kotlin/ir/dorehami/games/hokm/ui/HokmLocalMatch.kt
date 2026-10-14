package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.protocol.PlayerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Solo table: the human in seat 0, deterministic bots in the other seats. The only place in solo
 * play that calls [GameEngine.submit].
 */
class HokmLocalMatch(
    scope: CoroutineScope,
    playerName: String,
    playerCount: Int,
    targetPoints: Int,
    seed: Long,
    botLevel: String = HokmModule.BOT_PRO,
    onSound: (String) -> Unit,
    /**
     * HK-011: a previously saved match rebuilt by [HokmSave.load] (deterministic replay).
     * `null` starts a fresh match from [seed]. Passing the engine keeps rotation and process
     * death on the exact same deal, hand and turn instead of silently restarting.
     */
    restored: GameEngine? = null,
    /** HK-011: called once at construction and after every accepted action to persist the record. */
    private val onPersist: ((MatchRecord) -> Unit)? = null,
    /** HK-011: a safe error to show when a previous save could not be trusted. */
    initialError: String? = null,
) : HokmTable(
    scope = scope,
    seats = (0 until playerCount).map { seat ->
        if (seat == HUMAN_SEAT) SeatInfo(0, playerName.ifBlank { "شما" }, isBot = false)
        else SeatInfo(seat, BOT_NAMES[seat - 1], isBot = true)
    },
    mySeat = HUMAN_SEAT,
    onSound = onSound,
) {
    companion object {
        const val HUMAN_SEAT = 0
        private val BOT_NAMES = listOf("رضا", "مریم", "امیر")
    }

    private val players = listOf(PlayerId("p_me"), PlayerId("p_bot1"), PlayerId("p_bot2"), PlayerId("p_bot3")).take(playerCount)

    private val engine: GameEngine = restored ?: GameEngine.start(
        HokmModule,
        GameConfig(
            seed = seed,
            players = players,
            options = buildJsonObject {
                put(HokmSession.Options.TARGET_POINTS, targetPoints)
                put(HokmSession.Options.BOT_LEVEL, botLevel)
            },
            clock = ManualClock(),
            rng = SplitMixRng(seed),
        ),
    )

    init {
        _state.value = _state.value.copy(
            modeLabel = if (botLevel == HokmModule.BOT_EASY) "ربات ساده" else "ربات حرفه‌ای",
            error = initialError,
        )
        // Persist right away so the seed survives a rotation made before the first move.
        onPersist?.invoke(engine.record())
    }

    /** The record of this match so far (for saving / verification later). */
    fun record() = engine.record()

    private fun persist() {
        onPersist?.invoke(engine.record())
    }

    override fun privateView(): JsonObject = engine.session.privateView(players[mySeat])

    override fun isFinished(): Boolean = engine.session.phase == Phase.FINISHED

    override fun sendMine(action: GameAction) {
        val r = engine.submit(players[mySeat], action)
        if (r is ApplyResult.Ok) persist()
        onResult(r, mySeat, engine.session.version)
    }

    override suspend fun botStep(s: HokmUiState, actor: Int): Boolean {
        delay(
            when (s.handPhase) {
                HandPhase.CHOOSING_HOKM -> 1400L
                HandPhase.DISCARDING -> 1200L
                HandPhase.DRAWING -> 450L
                HandPhase.PLAYING -> if (s.tableCards.isEmpty()) BOT_LEAD_MS else BOT_THINK_MS
            },
        )
        val r = engine.submitBot(players[actor])
        if (r == null) {
            // Should never happen: the bot policy always has a move when it is its turn.
            setError("ربات حرکتی پیدا نکرد")
            delay(1000)
        } else {
            if (r is ApplyResult.Ok) persist()
            onResult(r, actor, engine.session.version)
        }
        return true
    }
}
