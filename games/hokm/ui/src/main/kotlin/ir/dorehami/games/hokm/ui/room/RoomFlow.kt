package ir.dorehami.games.hokm.ui.room

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.uikit.toFa
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.SessionClient
import ir.dorehami.transport.TransportEvent
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.transport.nearby.NearbyTransport
import ir.dorehami.transport.online.OnlineApi
import ir.dorehami.transport.online.OnlineException
import ir.dorehami.transport.online.WebSocketTransport
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom

enum class RoomMode { BLUETOOTH, ONLINE }

/** Where the multiplayer flow is, from the first tap to the table. */
sealed interface RoomStage {
    /** Pick: host / join (Bluetooth) or create / code / random (online). */
    data object Choose : RoomStage

    data class Connecting(val message: String) : RoomStage

    /** Guest side on Bluetooth: rooms visible nearby. */
    data object Discovering : RoomStage

    data class Lobby(val client: SessionClient, val code: String, val canStart: Boolean, val hint: String) : RoomStage

    data class Playing(val client: SessionClient) : RoomStage

    data class Failed(val message: String) : RoomStage
}

/**
 * Builds the right transport + controller for the chosen mode and hands the screen a [SessionClient].
 * Nothing here knows Hokm beyond its module id and options — a second game reuses it as is.
 */
class RoomFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    val mode: RoomMode,
    private val playerName: String,
    private val playerCount: Int,
    private val targetPoints: Int,
    private val serverUrl: String,
) {
    private val _stage = mutableStateOf<RoomStage>(RoomStage.Choose)
    val stage: State<RoomStage> get() = _stage
    private val connectionPlan = OnlineConnectionPlan(serverUrl)
    private val _serverAddressError = mutableStateOf<String?>(null)
    val serverAddressError: State<String?> get() = _serverAddressError

    private val me = PlayerInfo(PlayerId("p_" + randomCode(8).lowercase()), playerName.ifBlank { "مهمان" })
    private val options = buildJsonObject { put(HokmSession.Options.TARGET_POINTS, targetPoints) }

    private var nearby: NearbyTransport? = null
    private var ws: WebSocketTransport? = null
    private var http: HttpClient? = null
    private var client: SessionClient? = null
    private var watcher: Job? = null
    private val resources = RoomResources()

    val nearbyRooms: StateFlow<List<NearbyRoom>>? get() = nearby?.discovered

    // ------------------------------------------------------------------ Bluetooth

    fun hostBluetooth() = scope.launch {
        var connected = false
        try {
            _stage.value = RoomStage.Connecting("در حال ساخت اتاق…")
            val code = randomCode(5)
            val t = resources.track("nearby", NearbyTransport(context, localPeer = "host")) { it.close() }
            nearby = t
            val host = HostController(
                module = HokmModule, transport = t, scope = scope, roomCode = code, roomName = "اتاق ${me.displayName}",
                hostPlayer = me, options = options, seed = SecureRandom().nextLong(),
                clock = { System.currentTimeMillis() }, rngFactory = { SplitMixRng(it) },
                partySize = playerCount,
            )
            host.open()
            client = resources.track("client", host) { it.leave() }
            if (!t.advertise(code, me.displayName)) {
                _stage.value = RoomStage.Failed("بلوتوث در دسترس نیست. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
                return@launch
            }
            _stage.value = RoomStage.Lobby(host, code, canStart = true, hint = "دوستانت «پیوستن» را بزنند و اتاق تو را انتخاب کنند. صندلی خالی = ربات.")
            watchStart(host)
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    fun joinBluetooth() = scope.launch {
        var connected = false
        try {
            _stage.value = RoomStage.Connecting("در حال جست‌وجوی اتاق‌های اطراف…")
            val t = resources.track("nearby", NearbyTransport(context, localPeer = "guest")) { it.close() }
            nearby = t
            if (!t.discover()) {
                _stage.value = RoomStage.Failed("جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
                return@launch
            }
            _stage.value = RoomStage.Discovering
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    fun connectBluetooth(room: NearbyRoom) = scope.launch {
        val t = nearby ?: return@launch
        var connected = false
        try {
            _stage.value = RoomStage.Connecting("در حال اتصال به اتاق ${room.roomCode}…")
            t.stopDiscovery()
            if (!t.connectTo(room, me.displayName)) {
                _stage.value = RoomStage.Failed("میزبان اتصال را نپذیرفت. نزدیک‌تر بیا و دوباره امتحان کن.")
                return@launch
            }
            val guest = GuestController(
                t,
                scope,
                roomCode = room.roomCode,
                hostPeer = room.endpointId,
                player = me,
                nowMs = { System.currentTimeMillis() },
            )
            client = resources.track("client", guest) { it.leave() }
            guest.join()
            // End-to-end proof of the link: `NearbyTransport` queues the join envelope until GMS
            // confirms the connection and flushes it, so a lobby.ack means the room works, not just
            // that requestConnection() returned. Waiting on PeerConnected used to deadlock for 20 s.
            val st = withTimeoutOrNull(LINK_TIMEOUT_MS) { guest.state.first { it.room != null } }
            if (st == null) {
                _stage.value = RoomStage.Failed("اتصال برقرار نشد. نزدیک‌تر بیا و دوباره امتحان کن.")
                return@launch
            }
            val lobby = RoomStage.Lobby(
                guest,
                room.roomCode,
                canStart = st.isHost,
                hint = "منتظر میزبان بمان؛ وقتی شروع کند میز باز می‌شود.",
            )
            _stage.value = lobby
            watchStart(guest)
            watchBluetoothLink(t, room, guest, lobby)
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    /**
     * Keeps a Bluetooth guest on the table after a dropped link.
     *
     * Walking away from the phone or a brief radio hiccup closes the Nearby endpoint; without this
     * the lobby showed "connection lost" forever. Re-requesting the connection makes the host
     * auto-accept, and `GuestController` re-joins with `resume = true`, so the host re-sends state.
     */
    private fun watchBluetoothLink(
        t: NearbyTransport,
        room: NearbyRoom,
        guest: GuestController,
        lobby: RoomStage.Lobby,
    ) {
        val job = scope.launch {
            var reconnecting = false
            // Never yank the table away when the link drops mid-match: reconnection is silent there,
            // because the host resends state as soon as the guest rejoins.
            val showStage: (RoomStage) -> Unit = { next ->
                if (_stage.value !is RoomStage.Playing) _stage.value = next
            }
            guest.state.collect { st ->
                if (reconnecting || st.connected || st.room == null) return@collect
                reconnecting = true
                launch {
                    var attempt = 0
                    while (attempt < BLUETOOTH_RETRIES && !guest.state.value.connected) {
                        attempt++
                        showStage(RoomStage.Connecting("ارتباط قطع شد · تلاش ${attempt.toFa()}…"))
                        runCatching { t.connectTo(room, me.displayName) }
                        withTimeoutOrNull(RECONNECT_TIMEOUT_MS) { guest.state.first { it.connected } }
                        if (!guest.state.value.connected) delay(1_500L * attempt)
                    }
                    reconnecting = false
                    if (guest.state.value.connected) {
                        showStage(lobby)
                    } else {
                        showStage(RoomStage.Failed("ارتباط با میزبان قطع شد. نزدیک‌تر شو و اتاق را دوباره انتخاب کن."))
                    }
                }
            }
        }
        resources.track("link-watcher") { job.cancel() }
    }

    // ------------------------------------------------------------------ Online

    /** The next create/join/random action reads the latest value typed in the lobby. */
    fun updateServerUrl(value: String) {
        connectionPlan.update(value)
        _serverAddressError.value = null
    }

    fun createOnline() = online("در حال ساخت اتاق…") { api, token ->
        api.createRoom(token, HokmModule.ID, playerCount, options, "اتاق ${me.displayName}").code
    }

    fun joinOnline(code: String) = online("در حال پیوستن به اتاق…") { api, token ->
        api.joinRoom(token, code.trim().uppercase()).code
    }

    fun randomOnline() = online("در حال پیدا کردن حریف…") { api, token ->
        var ticket = api.enqueue(token, HokmModule.ID, playerCount)
        while (ticket.roomCode == null) {
            _stage.value = RoomStage.Connecting("منتظر حریف… (اگر کسی نیاید، با ربات شروع می‌شود)")
            ticket = api.pollTicket(ticket.id, 20)
        }
        api.joinRoom(token, ticket.roomCode!!).code
    }

    private fun online(message: String, resolveRoom: suspend (OnlineApi, String) -> String) = scope.launch {
        // Resolve once, before creating any network resource. Both the HTTP client and the socket
        // use this same endpoint, so a stale address can never be used for one of them.
        val endpoint = connectionPlan.resolve()
        if (endpoint == null) {
            _serverAddressError.value = OnlineConnectionPlan.ERROR_FA
            return@launch
        }
        _serverAddressError.value = null
        _stage.value = RoomStage.Connecting(message)
        var connected = false
        try {
            val http = resources.track("http", HttpClient { install(WebSockets) }) { it.close() }
            this@RoomFlow.http = http
            val api = OnlineApi(http, endpoint.httpBase)
            val login = api.guestLogin(me.displayName)
            val code = resolveRoom(api, login.token)
            val t = resources.track("ws", WebSocketTransport(http, endpoint.socketUrl(code, login.token), scope, localPeer = login.player.id.value)) { it.close() }
            ws = t
            t.connect()
            val guest = GuestController(t, scope, roomCode = code, hostPeer = WebSocketTransport.SERVER_PEER, player = login.player, nowMs = { System.currentTimeMillis() })
            client = resources.track("client", guest) { it.leave() }
            guest.join()
            // lobby.ack tells us whether we own the room.
            val st = withTimeoutOrNull(10_000) { guest.state.first { it.room != null } }
            if (st == null) {
                _stage.value = RoomStage.Failed("سرور جواب نداد.")
                return@launch
            }
            _stage.value = RoomStage.Lobby(
                guest, code,
                canStart = st.isHost,
                hint = if (st.isHost) "کد اتاق را برای دوستانت بفرست. صندلی خالی = ربات." else "منتظر سازندهٔ اتاق بمان.",
            )
            watchStart(guest)
            connected = true
        } catch (e: OnlineException) {
            _stage.value = RoomStage.Failed(e.messageFa)
        } catch (e: Exception) {
            _stage.value = RoomStage.Failed("اتصال به سرور برقرار نشد (${e.javaClass.simpleName}). آدرس سرور و اینترنت را چک کن.")
        } finally {
            // Any early return, thrown OnlineException or cancellation lands here.
            if (!connected) teardown()
        }
    }

    // ------------------------------------------------------------------ common

    /** Host / room owner presses start. */
    fun start() = scope.launch {
        when (val c = client) {
            is HostController -> c.startMatch()
            is GuestController -> c.requestStart()
            else -> Unit
        }
    }

    private fun watchStart(c: SessionClient) {
        watcher?.cancel()
        val job = scope.launch {
            c.state.first { it.room?.started == true }
            nearby?.stopAdvertising()
            _stage.value = RoomStage.Playing(c)
        }
        watcher = job
        resources.track("watcher") { job.cancel() }
    }

    fun leave() {
        teardown()
    }

    /**
     * Releases everything created for the current connection, exactly once.
     *
     * HokmRoom cancels [scope] immediately after `leave()` (R-05), and most closers are suspend
     * calls. A short-lived independent Job (not `launch(NonCancellable)`, which upstream warns
     * against because it severs the parent-child relation) keeps cleanup alive until it settles,
     * then completes itself. Fields are cleared synchronously so a later attempt cannot reuse a
     * half-closed client, and [RoomResources.releaseAll] makes a repeated `leave()` a no-op.
     */
    private fun teardown() {
        val job: CompletableJob = Job()
        CoroutineScope(job + Dispatchers.Main.immediate).launch {
            try {
                resources.releaseAll()
            } catch (e: Exception) {
                // Defensive: releaseAll already isolates every action. Never let a teardown
                // coroutine fail into the global exception handler.
                Unit
            } finally {
                job.complete()
            }
        }
        nearby = null
        ws = null
        http = null
        client = null
        watcher = null
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** How long a fresh Bluetooth join may take to produce a lobby ack. */
        private const val LINK_TIMEOUT_MS = 25_000L

        /** Attempts at rebuilding a dropped Nearby link before giving up. */
        private const val BLUETOOTH_RETRIES = 3
        private const val RECONNECT_TIMEOUT_MS = 10_000L

        /** Normalizes pasted input to exactly the alphabet used by generated room codes. */
        fun normalizeRoomCodeInput(value: String): String = value.uppercase().filter { it in ALPHABET }.take(5)

        fun randomCode(len: Int): String {
            val r = SecureRandom()
            return buildString { repeat(len) { append(ALPHABET[r.nextInt(ALPHABET.length)]) } }
        }
    }
}
