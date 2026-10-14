package ir.dorehami.session

import ir.dorehami.gamesdk.GameAction
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A seat that is not the authority: forwards my actions to the host, mirrors whatever views the
 * host sends. Out-of-order or stale `seq` is ignored (the host's seq is the only clock).
 */
class GuestController(
    private val transport: GameTransport,
    private val scope: CoroutineScope,
    val roomCode: String,
    private val hostPeer: String,
    private val player: PlayerInfo,
    private val nowMs: () -> Long = { 0L },
) : SessionClient {
    val me: PlayerId = player.id

    private val _state = MutableStateFlow(ClientState(me = me))
    override val state: StateFlow<ClientState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<SessionEvent> = _events.asSharedFlow()
    private var collector: Job? = null
    private var lastSeq = -1L
    /** my clock − host clock, from the latest envelope. */
    private var clockSkewMs = 0L

    /** Starts listening and asks the host for a seat. */
    suspend fun join() {
        if (collector == null) {
            collector = scope.launch { transport.events.collect { onTransport(it) } }
        }
        send(Msg.LOBBY_JOIN, buildJsonObject { put("player", player.toJson()); put("resume", _state.value.room != null); put("version", lastSeq) })
    }

    /** Owner of a server-hosted room: ask the authority to start (bots fill empty seats). */
    suspend fun requestStart() = send(Msg.LOBBY_START, JsonObject(emptyMap()))

    override suspend fun ready(ready: Boolean) = send(Msg.LOBBY_READY, buildJsonObject { put("ready", ready) })

    override suspend fun send(action: GameAction) =
        send(Msg.SESSION_ACTION, buildJsonObject { put("type", action.type); put("payload", action.payload) })

    override suspend fun emoji(id: String) {
        _events.tryEmit(SessionEvent.Emoji(me, id))
        send(Msg.CHAT_EMOJI, buildJsonObject { put("id", id) })
    }

    override suspend fun resync() = send(Msg.SESSION_RESYNC_REQUEST, JsonObject(emptyMap()))

    override suspend fun leave() {
        send(Msg.LOBBY_LEAVE, JsonObject(emptyMap()))
        collector?.cancel()
        transport.close()
    }

    private suspend fun send(type: String, payload: JsonObject) {
        transport.send(hostPeer, Envelope(type = type, room = roomCode, from = me.value, payload = payload, to = hostPeer, ts = nowMs()).encode())
    }

    private fun onTransport(ev: TransportEvent) {
        when (ev) {
            is TransportEvent.Message -> if (ev.from == hostPeer) onMessage(ev.text)
            is TransportEvent.PeerDisconnected -> if (ev.peer == hostPeer) {
                _state.value = _state.value.copy(connected = false)
                _events.tryEmit(SessionEvent.Error(ErrCodes.TRANSPORT_PEER_TIMEOUT, "ارتباط با میزبان قطع شد"))
            }
            TransportEvent.Closed -> _state.value = _state.value.copy(connected = false)
            // Link came back (transport reconnected): re-join with the same id → host resyncs us.
            is TransportEvent.PeerConnected -> if (ev.peer == hostPeer && _state.value.room != null) scope.launch { join() }
        }
    }

    private fun onMessage(text: String) {
        val env = Envelope.decode(text) ?: return
        if (env.room != roomCode) return
        if (env.ts > 0) clockSkewMs = nowMs() - env.ts
        val p = env.payload
        when (env.type) {
            Msg.LOBBY_ACK -> {
                val members = membersFromJson(p["members"])
                _state.value = _state.value.copy(
                    room = RoomInfo(p.str("roomCode") ?: roomCode, p.str("roomName") ?: "", p.str("gameId") ?: "", members, p.str("started") == "true"),
                    seat = p["seat"]?.jsonPrimitive?.long?.toInt() ?: -1,
                    isHost = p.str("isHost") == "true",
                    connected = true,
                )
            }
            Msg.LOBBY_REJECTED -> _events.tryEmit(SessionEvent.Error(p.str("code") ?: ErrCodes.ROOM_CLOSED, messageFor(p.str("code"))))
            Msg.LOBBY_ROSTER -> room()?.let { r -> _state.value = _state.value.copy(room = r.copy(members = membersFromJson(p["members"]))) }
            Msg.LOBBY_START -> room()?.let { r ->
                _state.value = _state.value.copy(room = r.copy(members = membersFromJson(p["members"]), started = true), phase = "PLAYING", result = null)
                lastSeq = -1
            }
            Msg.LOBBY_LEAVE -> {
                _state.value = _state.value.copy(connected = false)
                _events.tryEmit(SessionEvent.Error(ErrCodes.ROOM_CLOSED, "میزبان اتاق را بست"))
            }
            Msg.SESSION_STATE -> applyState(p, env.seq)
            Msg.SESSION_PRIVATE -> applyPrivate(p, env.seq)
            Msg.SESSION_RESYNC -> {
                lastSeq = -1
                p.obj("state")?.let { applyState(it, env.seq) }
                p.obj("private")?.let { applyPrivate(it, env.seq) }
                room()?.let { r -> _state.value = _state.value.copy(room = r.copy(members = membersFromJson(p["members"]), started = true)) }
            }
            Msg.SESSION_EVENT -> when (p.str("kind")) {
                "public" -> _events.tryEmit(SessionEvent.Public(p.str("type") ?: return, p.obj("payload") ?: JsonObject(emptyMap()), env.seq))
                "feed" -> _events.tryEmit(SessionEvent.Feed(p.str("key") ?: return, p.obj("args") ?: JsonObject(emptyMap()), p.str("level") ?: "info", env.seq))
                "sound" -> _events.tryEmit(SessionEvent.Sound(p.str("name") ?: return, env.seq))
            }
            Msg.SESSION_FINISHED -> _state.value = _state.value.copy(result = p, phase = "FINISHED")
            Msg.CHAT_EMOJI -> {
                val from = p.str("player") ?: return
                if (from != me.value) _events.tryEmit(SessionEvent.Emoji(PlayerId(from), p.str("id") ?: return))
            }
            Msg.SYS_ERROR -> _events.tryEmit(SessionEvent.Error(p.str("code") ?: ErrCodes.SERVER_INTERNAL, p.str("messageFa") ?: messageFor(p.str("code"))))
        }
    }

    private fun room(): RoomInfo? = _state.value.room

    private fun applyState(p: JsonObject, seq: Long) {
        if (seq < lastSeq) return
        lastSeq = seq
        // Deadline is translated into *my* clock using the skew measured from the envelope `ts`.
        val hostDeadline = p["deadlineMs"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        _state.value = _state.value.copy(
            publicView = p.obj("view") ?: JsonObject(emptyMap()),
            phase = p.str("phase") ?: _state.value.phase,
            seq = seq,
            deadlineMs = if (hostDeadline > 0) hostDeadline + clockSkewMs else 0L,
            turnSeconds = p["turnSeconds"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }

    private fun applyPrivate(p: JsonObject, seq: Long) {
        if (seq < lastSeq) return
        _state.value = _state.value.copy(
            privateView = p.obj("view") ?: JsonObject(emptyMap()),
            legalActions = p.strList("actions"),
        )
    }

    private fun messageFor(code: String?): String = when (code) {
        ErrCodes.ROOM_FULL -> "اتاق پر است"
        ErrCodes.ROOM_IN_PROGRESS -> "بازی در این اتاق شروع شده"
        ErrCodes.ROOM_NOT_FOUND -> "اتاقی با این کد پیدا نشد"
        ErrCodes.ROOM_CLOSED -> "اتاق بسته شد"
        else -> "خطا در ارتباط"
    }
}
