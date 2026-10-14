package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.session.ClientState
import ir.dorehami.session.SessionClient
import ir.dorehami.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A seat at a shared table (Bluetooth room or online room). The host's [ir.dorehami.session.HostController]
 * is the only authority; this class just mirrors my private view and the room's events into the
 * same [HokmUiState] the solo table uses — so the screen is byte-for-byte the same.
 */
class HokmSessionMatch(
    scope: CoroutineScope,
    private val client: SessionClient,
    private val modeLabel: String,
    onSound: (String) -> Unit,
) : HokmTable(
    scope = scope,
    seats = seatsOf(client.state.value),
    mySeat = client.state.value.seat.coerceAtLeast(0),
    onSound = onSound,
) {
    companion object {
        fun seatsOf(s: ClientState): List<SeatInfo> =
            s.room?.members?.sortedBy { it.seat }?.map { SeatInfo(it.seat, it.player.displayName, isBot = it.isBot) } ?: emptyList()
    }

    private val seatOfPlayer: Map<String, Int> =
        client.state.value.room?.members?.associate { it.player.id.value to it.seat } ?: emptyMap()
    private var jobs: List<Job> = emptyList()

    /** Waits for the first view from the host, then runs the usual intro + loop. */
    fun begin() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch {
                client.state.first { it.privateView.containsKey("hakemDraw") }
                start()
                client.state.collect { onState(it) }
            },
            scope.launch { client.events.collect { onEvent(it) } },
        )
    }

    override fun privateView(): JsonObject? = client.state.value.privateView.takeIf { it.containsKey("hakemDraw") }

    override fun isFinished(): Boolean = client.state.value.result != null || client.state.value.phase == "FINISHED"

    override fun sendMine(action: GameAction) {
        scope.launch { client.send(action) }
    }

    /** Never moves anyone locally: the host does, and events bring the result. */
    override suspend fun botStep(s: HokmUiState, actor: Int): Boolean = false

    override fun onStickerSent(sticker: Sticker) {
        scope.launch { client.emoji(sticker.id) }
    }

    private fun onState(s: ClientState) {
        if (!s.connected) setError("ارتباط قطع شد — در حال تلاش برای برگشت…")
        val ui0 = _state.value
        if (ui0.turnDeadlineMs != s.deadlineMs || ui0.turnSeconds != s.turnSeconds || ui0.modeLabel != modeLabel) {
            _state.value = ui0.copy(turnDeadlineMs = s.deadlineMs, turnSeconds = s.turnSeconds, modeLabel = modeLabel)
        }
        // Refresh only when the table is calm; during trick/hand animations the loop refreshes itself.
        val ui = _state.value
        if (pendingPauseMs == 0L && ui.collectingTo == null && ui.introShown == null && !ui.dealing) refresh()
        wake.trySend(Unit)
    }

    private fun onEvent(e: SessionEvent) {
        when (e) {
            // Events are enqueued as versioned notices (HK-010) — the wire `seq` is the host's
            // session version, shared with snapshots, so bursts and resyncs stay aligned.
            is SessionEvent.Public -> {
                val actor = e.payload["seat"]?.jsonPrimitive?.intOrNull ?: e.payload["hakem"]?.jsonPrimitive?.intOrNull ?: -1
                offerNotice(e.seq, actor, listOf(Emission.Public(e.type, e.payload)))
            }
            is SessionEvent.Sound -> offerNotice(e.seq, -1, listOf(Emission.Sound(e.name)))
            is SessionEvent.Emoji -> seatOfPlayer[e.from.value]?.takeIf { it != mySeat }?.let { showRemoteSticker(it, e.id) }
            is SessionEvent.Error -> setError(e.messageFa)
            is SessionEvent.Feed -> Unit
        }
        wake.trySend(Unit)
    }
}
