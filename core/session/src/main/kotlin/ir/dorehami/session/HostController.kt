package ir.dorehami.session

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Clock
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.Rng
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The room authority (docs/ARCHITECTURE.md): owns the lobby and the [GameEngine], is the only one
 * that applies actions, and fans views out. Works identically over Bluetooth, the internet relay or
 * an in-memory hub because it only ever sees a [GameTransport].
 *
 * The host phone is itself a player: [state]/[events] reflect the host's seat without a transport hop.
 */
class HostController(
    private val module: GameModule,
    private val transport: GameTransport,
    private val scope: CoroutineScope,
    val roomCode: String,
    val roomName: String,
    /** The host's own seat. `null` = a headless authority (the server): no seat, it only arbitrates. */
    hostPlayer: PlayerInfo?,
    private val options: JsonObject = module.defaultOptions(),
    private val seed: Long,
    private val clock: Clock,
    private val rngFactory: (Long) -> Rng,
    partySize: Int = module.preferredPartySize(),
    /** Pause before each bot move so humans can follow. 0 in tests. */
    private val botDelayMs: Long = 900,
    /** When a human drops mid-game the bot plays their seat until they resync. */
    private val botTakeoverOnDisconnect: Boolean = true,
    private val botNamesFa: List<String> = listOf("ربات آبی", "ربات سبز", "ربات قرمز"),
    /** Called once with the full replayable record when the match ends (persist it here). */
    private val onFinished: ((ir.dorehami.gamesdk.MatchRecord) -> Unit)? = null,
    /** Time a human has to move before the bot moves for them. 0 = no limit (solo / friends). */
    private val turnMillis: Long = 0L,
) : SessionClient {
    val partySize: Int = partySize.coerceIn(module.meta.minPlayers, module.meta.maxPlayers)
    val hostPlays: Boolean = hostPlayer != null
    val me: PlayerId = hostPlayer?.id ?: PlayerId("authority")
    val isStarted: Boolean get() = started

    private val lock = Mutex()
    private val members = LinkedHashMap<PlayerId, Member>()
    private val peerOf = HashMap<PlayerId, String>()
    private val playerOfPeer = HashMap<String, PlayerId>()
    private var engine: GameEngine? = null
    private var started = false
    private var finishedSent = false
    private var collector: Job? = null
    private var turnTimer: Job? = null
    private var deadlineMs: Long = 0L

    private val _state = MutableStateFlow(ClientState(me = me, seat = 0, isHost = true))
    override val state: StateFlow<ClientState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<SessionEvent> = _events.asSharedFlow()

    init {
        if (hostPlayer != null) members[me] = Member(hostPlayer, seat = 0, ready = true, connected = true, isHost = true)
        publishRoom()
    }

    /** Headless mode: the first human to join owns the room and may press start. */
    private var ownerId: PlayerId? = hostPlayer?.id

    /** Begins listening to the transport. Call once. */
    fun open() {
        if (collector != null) return
        collector = scope.launch {
            transport.events.collect { ev -> lock.withLock { onTransport(ev) } }
        }
    }

    /** The host's "start" button: empty seats become bots. */
    suspend fun startMatch(): Boolean = lock.withLock { startLocked() }

    private suspend fun startLocked(): Boolean {
        if (started) return false
        val humans = members.values.sortedBy { it.seat }
        if (humans.size > partySize) return false
        var botIdx = 0
        for (seat in 0 until partySize) {
            if (members.values.none { it.seat == seat }) {
                val id = PlayerId("bot_${seat}")
                members[id] = Member(
                    PlayerInfo(id, botNamesFa.getOrElse(botIdx) { "ربات ${seat + 1}" }, "bot"),
                    seat = seat, ready = true, connected = true, isHost = false, isBot = true,
                )
                botIdx++
            }
        }
        val players = members.values.sortedBy { it.seat }.map { it.player.id }
        val config = GameConfig(seed = seed, players = players, options = module.validateOptions(options), clock = clock, rng = rngFactory(seed))
        val eng = GameEngine.start(module, config)
        engine = eng
        started = true
        broadcast(
            Msg.LOBBY_START,
            buildJsonObject {
                put("matchId", "m_$roomCode")
                put("gameId", module.meta.id.value)
                put("options", config.options)
                put("players", buildJsonArray { members.values.sortedBy { it.seat }.forEach { add(it.player.toJson()) } })
                put("members", membersJson(members.values))
                put("startedAtMs", clock.nowMillis())
            },
        )
        publishRoom()
        pushViews()
        runBots()
        return true
    }

    // ---- SessionClient (host seat, loopback) ----

    override suspend fun ready(ready: Boolean) = Unit // host is always ready

    override suspend fun send(action: GameAction) = lock.withLock { applyFrom(me, action) }

    override suspend fun emoji(id: String) = lock.withLock {
        _events.tryEmit(SessionEvent.Emoji(me, id))
        broadcast(Msg.CHAT_EMOJI, buildJsonObject { put("id", id); put("player", me.value) })
    }

    override suspend fun resync() = lock.withLock { pushViews() }

    override suspend fun leave() {
        lock.withLock { broadcast(Msg.LOBBY_LEAVE, buildJsonObject { put("reason", "host_left") }) }
        collector?.cancel()
        turnTimer?.cancel()
        transport.close()
    }

    // ---- transport side ----

    private suspend fun onTransport(ev: TransportEvent) {
        when (ev) {
            is TransportEvent.Message -> onMessage(ev.from, ev.text)
            is TransportEvent.PeerDisconnected -> onPeerGone(ev.peer)
            is TransportEvent.PeerConnected -> Unit
            TransportEvent.Closed -> Unit
        }
    }

    private suspend fun onMessage(peer: String, text: String) {
        val env = Envelope.decode(text)
        if (env == null) {
            sendTo(peer, Msg.SYS_ERROR, buildJsonObject { put("code", ErrCodes.PROTO_MALFORMED) })
            return
        }
        if (env.room != roomCode) {
            sendTo(peer, Msg.SYS_ERROR, buildJsonObject { put("code", ErrCodes.ROOM_NOT_FOUND) })
            return
        }
        when (env.type) {
            Msg.LOBBY_JOIN -> onJoin(peer, env.payload)
            Msg.LOBBY_READY -> playerOfPeer[peer]?.let { pid ->
                members[pid]?.let { members[pid] = it.copy(ready = env.payload.str("ready") != "false") }
                roster()
            }
            Msg.LOBBY_LEAVE -> onPeerGone(peer, explicit = true)
            Msg.LOBBY_START -> {
                val pid = playerOfPeer[peer]
                if (pid != null && pid == ownerId) {
                    if (!startLocked()) reject(peer, ErrCodes.BAD_STATE, "هنوز نمی‌شود شروع کرد")
                } else {
                    reject(peer, ErrCodes.AUTH_UNAUTHORIZED, "فقط سازندهٔ اتاق می‌تواند شروع کند")
                }
            }
            Msg.SESSION_ACTION -> {
                val pid = playerOfPeer[peer] ?: return reject(peer, ErrCodes.AUTH_NOT_IN_ROOM, "شما عضو این اتاق نیستید")
                val type = env.payload.str("type") ?: return reject(peer, ErrCodes.BAD_PAYLOAD, "حرکت بدون نوع")
                applyFrom(pid, GameAction(type, env.payload.obj("payload") ?: JsonObject(emptyMap())))
            }
            Msg.SESSION_RESYNC_REQUEST -> playerOfPeer[peer]?.let { pushViewsTo(it, resync = true) }
            Msg.CHAT_EMOJI -> {
                val pid = playerOfPeer[peer] ?: return
                val id = env.payload.str("id") ?: return
                _events.tryEmit(SessionEvent.Emoji(pid, id))
                broadcast(Msg.CHAT_EMOJI, buildJsonObject { put("id", id); put("player", pid.value) })
            }
            else -> sendTo(peer, Msg.SYS_ERROR, buildJsonObject { put("code", ErrCodes.PROTO_UNKNOWN_TYPE); put("ref", env.id) })
        }
    }

    private suspend fun onJoin(peer: String, payload: JsonObject) {
        val info = payload.obj("player")?.let(PlayerInfo::fromJson)
            ?: return sendTo(peer, Msg.LOBBY_REJECTED, buildJsonObject { put("code", ErrCodes.BAD_PAYLOAD) })
        val existing = members[info.id]
        when {
            // Reconnect of a known player (same id): rebind and resync.
            existing != null && !existing.isBot -> {
                peerOf[info.id]?.let { playerOfPeer.remove(it) }
                peerOf[info.id] = peer
                playerOfPeer[peer] = info.id
                members[info.id] = existing.copy(connected = true, player = info)
            }
            started -> return sendTo(peer, Msg.LOBBY_REJECTED, buildJsonObject { put("code", ErrCodes.ROOM_IN_PROGRESS) })
            members.values.count { !it.isBot } >= partySize -> return sendTo(peer, Msg.LOBBY_REJECTED, buildJsonObject { put("code", ErrCodes.ROOM_FULL) })
            else -> {
                val seat = (0 until partySize).first { s -> members.values.none { it.seat == s } }
                members[info.id] = Member(info, seat = seat, ready = false, connected = true, isHost = ownerId == null)
                peerOf[info.id] = peer
                playerOfPeer[peer] = info.id
                if (ownerId == null) ownerId = info.id
            }
        }
        val m = members.getValue(info.id)
        sendTo(
            peer, Msg.LOBBY_ACK,
            buildJsonObject {
                put("ok", true)
                put("roomCode", roomCode)
                put("roomName", roomName)
                put("gameId", module.meta.id.value)
                put("seat", m.seat)
                put("isHost", m.isHost)
                put("started", started)
                put("members", membersJson(members.values))
            },
        )
        roster()
        if (started) pushViewsTo(info.id, resync = true)
    }

    private suspend fun onPeerGone(peer: String, explicit: Boolean = false) {
        val pid = playerOfPeer.remove(peer) ?: return
        peerOf.remove(pid)
        val m = members[pid] ?: return
        if (!started) {
            members.remove(pid)
        } else {
            members[pid] = m.copy(connected = false)
        }
        roster()
        if (started && botTakeoverOnDisconnect) runBots()
    }

    // ---- authority ----

    private suspend fun applyFrom(pid: PlayerId, action: GameAction) {
        val eng = engine ?: return error(pid, ErrCodes.BAD_STATE, "بازی هنوز شروع نشده")
        when (val r = eng.submit(pid, action)) {
            is ApplyResult.Rejected -> error(pid, r.code, r.messageFa)
            is ApplyResult.Ok -> {
                fanOut(r.emissions)
                pushViews()
                runBots()
            }
        }
    }

    /** Bots (and abandoned seats) move until it is a human's turn or the game ends. */
    private suspend fun runBots() {
        val eng = engine ?: return
        var guard = 0
        while (guard++ < 2000 && eng.session.result() == null) {
            val actor = members.values.sortedBy { it.seat }.firstOrNull { m ->
                (m.isBot || (!m.connected && botTakeoverOnDisconnect)) && eng.session.legalActions(m.player.id).isNotEmpty()
            } ?: break
            if (botDelayMs > 0) delay(botDelayMs)
            val r = eng.submitBot(actor.player.id) ?: break
            if (r !is ApplyResult.Ok) break
            fanOut(r.emissions)
            pushViews()
        }
        if (eng.session.result() != null && !finishedSent) {
            finishedSent = true
            val payload = buildJsonObject {
                put("result", resultJson(eng))
                put("record", recordJson(eng))
            }
            broadcast(Msg.SESSION_FINISHED, payload)
            _state.value = _state.value.copy(result = payload, phase = "FINISHED")
            onFinished?.invoke(eng.record())
        }
    }

    private suspend fun fanOut(emissions: List<Emission>) {
        val eng = engine ?: return
        for (e in emissions) {
            when (e) {
                is Emission.Public -> {
                    _events.tryEmit(SessionEvent.Public(e.type, e.payload, eng.session.version))
                    broadcast(Msg.SESSION_EVENT, buildJsonObject { put("kind", "public"); put("type", e.type); put("payload", e.payload) }, eng.session.version)
                }
                is Emission.Feed -> {
                    _events.tryEmit(SessionEvent.Feed(e.key, e.args, e.kind, eng.session.version))
                    broadcast(Msg.SESSION_EVENT, buildJsonObject { put("kind", "feed"); put("key", e.key); put("args", e.args); put("level", e.kind) }, eng.session.version)
                }
                is Emission.Sound -> {
                    _events.tryEmit(SessionEvent.Sound(e.name, eng.session.version))
                    broadcast(Msg.SESSION_EVENT, buildJsonObject { put("kind", "sound"); put("name", e.name) }, eng.session.version)
                }
                // Private emissions are never broadcast; the full private view goes point-to-point in pushViews.
                is Emission.Private -> Unit
            }
        }
    }

    private suspend fun pushViews() {
        armTurnTimer()
        for (m in members.values) if (!m.isBot) pushViewsTo(m.player.id, resync = false)
    }

    /**
     * If a connected human must move now, give them [turnSeconds]; when the deadline passes with the
     * same version, the bot policy moves for them (the game never stalls on one phone).
     */
    private fun armTurnTimer() {
        turnTimer?.cancel()
        turnTimer = null
        deadlineMs = 0L
        if (turnMillis <= 0L) return
        val eng = engine ?: return
        if (eng.session.result() != null) return
        val waitingOn = members.values.filter { !it.isBot && it.connected && eng.session.legalActions(it.player.id).isNotEmpty() }
        if (waitingOn.isEmpty()) return
        val version = eng.session.version
        deadlineMs = clock.nowMillis() + turnMillis
        turnTimer = scope.launch {
            delay(turnMillis)
            lock.withLock {
                val e = engine ?: return@withLock
                if (e.session.version != version || e.session.result() != null) return@withLock
                for (m in waitingOn) {
                    if (e.session.legalActions(m.player.id).isEmpty()) continue
                    val r = e.submitBot(m.player.id) ?: continue
                    if (r is ApplyResult.Ok) {
                        _events.tryEmit(SessionEvent.Feed("turn_timeout", buildJsonObject { put("seat", m.seat) }, "warn", e.session.version))
                        broadcast(Msg.SESSION_EVENT, buildJsonObject { put("kind", "feed"); put("key", "turn_timeout"); put("args", buildJsonObject { put("seat", m.seat) }); put("level", "warn") }, e.session.version)
                        fanOut(r.emissions)
                    }
                }
                pushViews()
                runBots()
            }
        }
    }

    private suspend fun pushViewsTo(pid: PlayerId, resync: Boolean) {
        val eng = engine ?: return
        val s = eng.session
        val pub = s.publicView()
        val priv = s.privateView(pid)
        val legal = s.legalActions(pid)
        if (pid == me) {
            _state.value = _state.value.copy(
                publicView = pub, privateView = priv, legalActions = legal,
                phase = s.phase.name, seq = s.version, deadlineMs = deadlineMs,
            )
            return
        }
        val peer = peerOf[pid] ?: return
        val statePayload = buildJsonObject {
            put("view", pub)
            put("seq", s.version)
            put("phase", s.phase.name)
            put("deadlineMs", deadlineMs)
            put("turnSeconds", (turnMillis / 1000L).toInt())
        }
        val privPayload = buildJsonObject {
            put("view", priv)
            put("seq", s.version)
            put("actions", buildJsonArray { legal.forEach { add(JsonPrimitive(it)) } })
        }
        if (resync) {
            sendTo(peer, Msg.SESSION_RESYNC, buildJsonObject { put("state", statePayload); put("private", privPayload); put("members", membersJson(members.values)) }, s.version)
        } else {
            sendTo(peer, Msg.SESSION_STATE, statePayload, s.version)
            sendTo(peer, Msg.SESSION_PRIVATE, privPayload, s.version)
        }
    }

    private suspend fun error(pid: PlayerId, code: String, messageFa: String) {
        if (pid == me) {
            _events.tryEmit(SessionEvent.Error(code, messageFa))
        } else {
            peerOf[pid]?.let { sendTo(it, Msg.SYS_ERROR, buildJsonObject { put("code", code); put("messageFa", messageFa) }) }
        }
    }

    private suspend fun reject(peer: String, code: String, messageFa: String) =
        sendTo(peer, Msg.SYS_ERROR, buildJsonObject { put("code", code); put("messageFa", messageFa) })

    private suspend fun roster() {
        broadcast(Msg.LOBBY_ROSTER, buildJsonObject { put("members", membersJson(members.values)) })
        publishRoom()
    }

    private fun publishRoom() {
        _state.value = _state.value.copy(
            room = RoomInfo(roomCode, roomName, module.meta.id.value, members.values.sortedBy { it.seat }, started),
        )
    }

    private suspend fun broadcast(type: String, payload: JsonObject, seq: Long = 0) {
        val text = Envelope(type = type, room = roomCode, from = me.value, payload = payload, seq = seq, ts = clock.nowMillis()).encode()
        transport.send(null, text)
    }

    private suspend fun sendTo(peer: String, type: String, payload: JsonObject, seq: Long = 0) {
        val text = Envelope(type = type, room = roomCode, from = me.value, payload = payload, to = peer, seq = seq, ts = clock.nowMillis()).encode()
        transport.send(peer, text)
    }

    private fun resultJson(eng: GameEngine): JsonObject {
        val res = eng.session.result()
        return buildJsonObject {
            put("matchId", "m_$roomCode")
            put("digest", "sha256:" + eng.digest())
            put(
                "ranking",
                buildJsonArray {
                    res?.standings?.forEach { st ->
                        st.members.forEach { p ->
                            add(buildJsonObject { put("player", p.value); put("seat", eng.config.seatOf(p)); put("rank", st.rank); put("points", st.score) })
                        }
                    }
                },
            )
        }
    }

    /**
     * Record *header* only: seed, players, options, digest and action count. The full action log is
     * kept by the host (and the server online) — it does not fit the 16KB envelope budget for a long
     * match and nobody needs it live. Clients fetch it later for replays/verification.
     */
    private fun recordJson(eng: GameEngine): JsonObject = buildJsonObject {
        put("schema", 1)
        put("seed", eng.config.seed)
        put("gameId", module.meta.id.value)
        put("options", eng.config.options)
        put("players", buildJsonArray { eng.config.players.forEach { add(JsonPrimitive(it.value)) } })
        put("actionCount", eng.actions.size)
        put("digest", eng.digest())
    }

    /** The complete, replayable record (docs/ARCHITECTURE.md 4.4) — persist this on the host. */
    fun record(): ir.dorehami.gamesdk.MatchRecord? = engine?.takeIf { it.session.result() != null }?.record()
}
