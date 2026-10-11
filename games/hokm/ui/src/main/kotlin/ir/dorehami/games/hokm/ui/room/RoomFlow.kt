package ir.dorehami.games.hokm.ui.room

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmSession
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    private val me = PlayerInfo(PlayerId("p_" + randomCode(8).lowercase()), playerName.ifBlank { "مهمان" })
    private val options = buildJsonObject { put(HokmSession.Options.TARGET_POINTS, targetPoints) }

    private var nearby: NearbyTransport? = null
    private var ws: WebSocketTransport? = null
    private var http: HttpClient? = null
    private var client: SessionClient? = null
    private var watcher: Job? = null

    val nearbyRooms: StateFlow<List<NearbyRoom>>? get() = nearby?.discovered

    // ------------------------------------------------------------------ Bluetooth

    fun hostBluetooth() = scope.launch {
        _stage.value = RoomStage.Connecting("در حال ساخت اتاق…")
        val code = randomCode(5)
        val t = NearbyTransport(context, localPeer = "host").also { nearby = it }
        val host = HostController(
            module = HokmModule, transport = t, scope = scope, roomCode = code, roomName = "اتاق ${me.displayName}",
            hostPlayer = me, options = options, seed = SecureRandom().nextLong(),
            clock = { System.currentTimeMillis() }, rngFactory = { SplitMixRng(it) },
            partySize = playerCount,
        )
        host.open()
        if (!t.advertise(code, me.displayName)) {
            _stage.value = RoomStage.Failed("بلوتوث در دسترس نیست. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
            return@launch
        }
        client = host
        _stage.value = RoomStage.Lobby(host, code, canStart = true, hint = "دوستانت «پیوستن» را بزنند و اتاق تو را انتخاب کنند. صندلی خالی = ربات.")
        watchStart(host)
    }

    fun joinBluetooth() = scope.launch {
        _stage.value = RoomStage.Connecting("در حال جست‌وجوی اتاق‌های اطراف…")
        val t = NearbyTransport(context, localPeer = "guest").also { nearby = it }
        if (!t.discover()) {
            _stage.value = RoomStage.Failed("جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
            return@launch
        }
        _stage.value = RoomStage.Discovering
    }

    fun connectBluetooth(room: NearbyRoom) = scope.launch {
        val t = nearby ?: return@launch
        _stage.value = RoomStage.Connecting("در حال اتصال به اتاق ${room.roomCode}…")
        t.stopDiscovery()
        // Listen for the accept before asking, so we cannot miss it.
        val accepted = scope.launch {
            t.events.first { it is TransportEvent.PeerConnected && it.peer == room.endpointId }
        }
        if (!t.connectTo(room, me.displayName)) {
            accepted.cancel()
            _stage.value = RoomStage.Failed("میزبان اتصال را نپذیرفت.")
            return@launch
        }
        val ok = withTimeoutOrNull(20_000) { accepted.join(); true } ?: false
        if (!ok) {
            _stage.value = RoomStage.Failed("اتصال برقرار نشد. نزدیک‌تر بیا و دوباره امتحان کن.")
            return@launch
        }
        val guest = GuestController(t, scope, roomCode = room.roomCode, hostPeer = room.endpointId, player = me, nowMs = { System.currentTimeMillis() })
        client = guest
        guest.join()
        _stage.value = RoomStage.Lobby(guest, room.roomCode, canStart = false, hint = "منتظر میزبان بمان؛ وقتی شروع کند میز باز می‌شود.")
        watchStart(guest)
    }

    // ------------------------------------------------------------------ Online

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
        _stage.value = RoomStage.Connecting(message)
        try {
            val http = HttpClient { install(WebSockets) }.also { this@RoomFlow.http = it }
            val api = OnlineApi(http, serverUrl)
            val login = api.guestLogin(me.displayName)
            val code = resolveRoom(api, login.token)
            val t = WebSocketTransport(http, api.roomSocketUrl(code, login.token), scope, localPeer = login.player.id.value).also { ws = it }
            t.connect()
            val guest = GuestController(t, scope, roomCode = code, hostPeer = WebSocketTransport.SERVER_PEER, player = login.player, nowMs = { System.currentTimeMillis() })
            client = guest
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
        } catch (e: OnlineException) {
            _stage.value = RoomStage.Failed(e.messageFa)
        } catch (e: Exception) {
            _stage.value = RoomStage.Failed("اتصال به سرور برقرار نشد (${e.javaClass.simpleName}). آدرس سرور و اینترنت را چک کن.")
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

    fun setReady(ready: Boolean) = scope.launch { client?.ready(ready) }

    private fun watchStart(c: SessionClient) {
        watcher?.cancel()
        watcher = scope.launch {
            c.state.first { it.room?.started == true }
            nearby?.stopAdvertising()
            _stage.value = RoomStage.Playing(c)
        }
    }

    fun leave() {
        watcher?.cancel()
        scope.launch {
            runCatching { client?.leave() }
            runCatching { nearby?.close() }
            runCatching { ws?.close() }
            runCatching { http?.close() }
        }
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        fun randomCode(len: Int): String {
            val r = SecureRandom()
            return buildString { repeat(len) { append(ALPHABET[r.nextInt(ALPHABET.length)]) } }
        }
    }
}
