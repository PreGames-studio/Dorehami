package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.ManualClock
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
 *
 * Every accepted move is reported through [onSaved] as a full [MatchRecord] (seed + action log +
 * result + digest), so the caller can persist it and the match survives killing the process
 * (HK-011). [restore] replays a record back into this table deterministically.
 */
class HokmLocalMatch(
    scope: CoroutineScope,
    playerName: String,
    playerCount: Int,
    targetPoints: Int,
    seed: Long,
    botLevel: String = HokmModule.BOT_PRO,
    onSound: (String) -> Unit,
    /** Fired after every accepted human or bot move with the current [MatchRecord]. */
    private val onSaved: (MatchRecord) -> Unit = {},
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

    private var engine: GameEngine = GameEngine.start(
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
        _state.value = _state.value.copy(modeLabel = if (botLevel == HokmModule.BOT_EASY) "ربات ساده" else "ربات حرفه‌ای")
    }

    /** The record of this match so far (for saving / verification later). */
    fun record() = engine.record()

    /**
     * The full match record: seed + accepted action log + result (+ digest). Pass it to
     * [restore] on a new [HokmLocalMatch] to resume exactly here.
     */
    fun save(): MatchRecord = engine.record()

    /**
     * Replaces this engine with a deterministic replay of [record]. A tampered record (an action
     * that no longer applies) throws [IllegalStateException] — treat that as "start a new match".
     * Transient UI flags are reset; the table redraws from the restored private view on the next
     * [refresh].
     */
    fun restore(record: MatchRecord) {
        engine = GameEngine.replay(HokmModule, record)
        _state.value = _state.value.copy(isSendingMove = false)
    }

    override fun privateView(): JsonObject = engine.session.privateView(players[mySeat])

    /**
     * What the human sees right now — hand, hakem draw, turn — as the engine's private view.
     * Public so the persistence tests (and a future resume screen) can read the very table state
     * that [save] and [restore] carry, without a device or a composition.
     */
    fun humanView(): JsonObject = privateView()

    override fun isFinished(): Boolean = engine.session.phase == Phase.FINISHED

    override fun sendMine(action: GameAction) {
        val result = engine.submit(players[mySeat], action)
        if (result is ApplyResult.Ok) onSaved(engine.record())
        onResult(result, mySeat)
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
            if (r is ApplyResult.Ok) onSaved(engine.record())
            onResult(r, actor)
        }
        return true
    }
}
