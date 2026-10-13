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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private data class OnlineConnectionResources(val http: HttpClient, val api: OnlineApi)

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
    private val serverAddress = OnlineServerAddress(serverUrl)
    private val _serverAddressError = mutableStateOf<String?>(null)
    val serverAddressError: State<String?> get() = _serverAddressError

    private val me = PlayerInfo(PlayerId("p_" + randomCode(8).lowercase()), playerName.ifBlank { "مهمان" })
    private val options = buildJsonObject { put(HokmSession.Options.TARGET_POINTS, targetPoints) }

    private val resources = RoomResourceOwner()
    private val operationLock = Any()
    private val operationJobs = mutableSetOf<Job>()
    private var closing = false
    private var leaving = false
    private val leaveLock = Any()
    private var leaveJob: Job? = null

    private var nearby: NearbyTransport? = null
    private var client: SessionClient? = null
    private var watcher: Job? = null

    val nearbyRooms: StateFlow<List<NearbyRoom>>? get() = nearby?.discovered

    // ------------------------------------------------------------------ Bluetooth

    fun hostBluetooth() = launchOperation {
        _stage.value = RoomStage.Connecting("در حال ساخت اتاق…")
        try {
            val code = randomCode(5)
            val t = NearbyTransport(context, localPeer = "host").also { nearby = it }
            if (!resources.register { t.close() }) throw CancellationException("Room is closing")
            val host = HostController(
                module = HokmModule, transport = t, scope = scope, roomCode = code, roomName = "اتاق ${me.displayName}",
                hostPlayer = me, options = options, seed = SecureRandom().nextLong(),
                clock = { System.currentTimeMillis() }, rngFactory = { SplitMixRng(it) },
                partySize = playerCount,
            )
            client = host
            if (!resources.register { host.leave() }) throw CancellationException("Room is closing")
            host.open()
            if (!t.advertise(code, me.displayName)) {
                failRoom("بلوتوث در دسترس نیست. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
                return@launchOperation
            }
            _stage.value = RoomStage.Lobby(host, code, canStart = true, hint = "دوستانت «پیوستن» را بزنند و اتاق تو را انتخاب کنند. صندلی خالی = ربات.")
            watchStart(host)
        } catch (e: CancellationException) {
            resources.close()
            throw e
        } catch (e: Exception) {
            failRoom("ساخت اتاق بلوتوثی انجام نشد. بلوتوث و اجازه‌ها را بررسی کن.")
        }
    }

    fun joinBluetooth() = launchOperation {
        _stage.value = RoomStage.Connecting("در حال جست‌وجوی اتاق‌های اطراف…")
        try {
            val t = NearbyTransport(context, localPeer = "guest").also { nearby = it }
            if (!resources.register { t.close() }) throw CancellationException("Room is closing")
            if (!t.discover()) {
                failRoom("جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
                return@launchOperation
            }
            _stage.value = RoomStage.Discovering
        } catch (e: CancellationException) {
            resources.close()
            throw e
        } catch (e: Exception) {
            failRoom("جست‌وجوی اتاق شروع نشد. بلوتوث و اجازه‌ها را بررسی کن.")
        }
    }

    fun connectBluetooth(room: NearbyRoom) = launchOperation {
        val t = nearby ?: return@launchOperation
        _stage.value = RoomStage.Connecting("در حال اتصال به اتاق ${room.roomCode}…")
        try {
            t.stopDiscovery()
            // Listen for the accept before asking, so we cannot miss it. Keep it a child of this attempt.
            val accepted = async {
                t.events.first { it is TransportEvent.PeerConnected && it.peer == room.endpointId }
            }
            val connected = try {
                if (!t.connectTo(room, me.displayName)) {
                    failRoom("میزبان اتصال را نپذیرفت.")
                    return@launchOperation
                }
                withTimeoutOrNull(20_000) { accepted.await(); true } ?: false
            } finally {
                accepted.cancel()
            }
            if (!connected) {
                failRoom("اتصال برقرار نشد. نزدیک‌تر بیا و دوباره امتحان کن.")
                return@launchOperation
            }
            val guest = GuestController(t, scope, roomCode = room.roomCode, hostPeer = room.endpointId, player = me, nowMs = { System.currentTimeMillis() })
            client = guest
            if (!resources.register { guest.leave() }) throw CancellationException("Room is closing")
            guest.join()
            _stage.value = RoomStage.Lobby(guest, room.roomCode, canStart = false, hint = "منتظر میزبان بمان؛ وقتی شروع کند میز باز می‌شود.")
            watchStart(guest)
        } catch (e: CancellationException) {
            resources.close()
            throw e
        } catch (e: Exception) {
            failRoom("اتصال به اتاق برقرار نشد. نزدیک‌تر بیا و دوباره امتحان کن.")
        }
    }

    // ------------------------------------------------------------------ Online

    /** The next create/join/random action reads the latest value typed in the lobby. */
    fun updateServerUrl(value: String) {
        serverAddress.update(value)
        _serverAddressError.value = null
    }

    fun createOnline() = online("در حال ساخت اتاق…") { api, token ->
        api.createRoom(token, HokmModule.ID, playerCount, options, "اتاق ${me.displayName}").code
    }

    fun joinOnline(code: String) = online("در حال پیوستن به اتاق…") { api, token ->
        api.joinRoom(token, code.trim().uppercase()).code
    }

    fun randomOnline() = online("در حال پیدا کردن حریف…") { api, token ->
        var ticketId: String? = null
        var matched = false
        try {
            var ticket = api.enqueue(token, HokmModule.ID, playerCount)
            ticketId = ticket.id
            while (ticket.roomCode == null) {
                _stage.value = RoomStage.Connecting("منتظر حریف… (اگر کسی نیاید، با ربات شروع می‌شود)")
                ticket = api.pollTicket(ticket.id, 20)
                ticketId = ticket.id
            }
            matched = true
            api.joinRoom(token, ticket.roomCode!!).code
        } finally {
            val pendingTicket = ticketId
            if (!matched && pendingTicket != null) {
                withContext(NonCancellable) {
                    runCatching { withTimeoutOrNull(1_000) { api.cancelTicket(pendingTicket) } }
                }
            }
        }
    }

    private fun online(message: String, resolveRoom: suspend (OnlineApi, String) -> String) = launchOperation {
        try {
            val connection = serverAddress.createForNextConnection(
                onInvalidAddress = { _serverAddressError.value = it },
                createConnection = { baseUrl ->
                    _serverAddressError.value = null
                    _stage.value = RoomStage.Connecting(message)
                    val http = HttpClient { install(WebSockets) }
                    if (!resources.register { http.close() }) throw CancellationException("Room is closing")
                    OnlineConnectionResources(http, OnlineApi(http, baseUrl))
                },
            ) ?: return@launchOperation
            val http = connection.http
            val api = connection.api
            val login = api.guestLogin(me.displayName)
            val code = resolveRoom(api, login.token)
            val transport = WebSocketTransport(http, api.roomSocketUrl(code, login.token), scope, localPeer = login.player.id.value)
            if (!resources.register { transport.close() }) throw CancellationException("Room is closing")
            transport.connect()
            val guest = GuestController(transport, scope, roomCode = code, hostPeer = WebSocketTransport.SERVER_PEER, player = login.player, nowMs = { System.currentTimeMillis() })
            client = guest
            if (!resources.register { guest.leave() }) throw CancellationException("Room is closing")
            guest.join()
            // lobby.ack tells us whether we own the room.
            val st = withTimeoutOrNull(10_000) { guest.state.first { it.room != null } }
            if (st == null) {
                failRoom("سرور جواب نداد.")
                return@launchOperation
            }
            _stage.value = RoomStage.Lobby(
                guest, code,
                canStart = st.isHost,
                hint = if (st.isHost) "کد اتاق را برای دوستانت بفرست. صندلی خالی = ربات." else "منتظر سازندهٔ اتاق بمان.",
            )
            watchStart(guest)
        } catch (e: CancellationException) {
            resources.close()
            throw e
        } catch (e: OnlineException) {
            failRoom(e.messageFa)
        } catch (e: Exception) {
            failRoom("اتصال به سرور برقرار نشد (${e.javaClass.simpleName}). آدرس سرور و اینترنت را چک کن.")
        }
    }

    // ------------------------------------------------------------------ common

    /** Host / room owner presses start. */
    fun start() = launchOperation {
        when (val c = client) {
            is HostController -> c.startMatch()
            is GuestController -> c.requestStart()
            else -> Unit
        }
    }

    fun setReady(ready: Boolean) = launchOperation { client?.ready(ready) }

    private fun watchStart(c: SessionClient) {
        watcher?.cancel()
        watcher = launchOperation {
            c.state.first { it.room?.started == true }
            nearby?.stopAdvertising()
            if (!isClosing()) _stage.value = RoomStage.Playing(c)
        }
    }

    private fun launchOperation(block: suspend CoroutineScope.() -> Unit): Job {
        val job = synchronized(operationLock) {
            if (closing) return Job().apply { cancel() }
            scope.launch(start = CoroutineStart.LAZY, block = block).also { operation ->
                operationJobs += operation
                operation.invokeOnCompletion {
                    synchronized(operationLock) { operationJobs -= operation }
                }
            }
        }
        job.start()
        return job
    }

    private fun isClosing(): Boolean = synchronized(operationLock) { closing }

    private suspend fun failRoom(message: String) {
        val currentJob = currentCoroutineContext()[Job]
        val (shouldReportFailure, otherJobs) = synchronized(operationLock) {
            val shouldReport = !closing
            closing = true
            shouldReport to operationJobs.filterNot { it === currentJob }
        }
        otherJobs.forEach { it.cancel() }
        watcher?.cancel()
        synchronized(operationLock) {
            if (shouldReportFailure && !leaving) _stage.value = RoomStage.Failed(message)
        }
        scope.cancel()
        withContext(NonCancellable) {
            withTimeoutOrNull(OPERATION_JOIN_TIMEOUT_MILLIS) { otherJobs.joinAll() }
            resources.close()
        }
    }

    /** Starts idempotent cleanup independently so composition disposal cannot cancel it. */
    fun leave(): Job = synchronized(leaveLock) {
        leaveJob ?: run {
            synchronized(operationLock) {
                closing = true
                leaving = true
            }
            val cleanupParent = SupervisorJob()
            val cleanupScope = CoroutineScope(cleanupParent + Dispatchers.IO)
            val cleanup = cleanupScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val jobs = synchronized(operationLock) { operationJobs.toList() }
                    jobs.forEach { it.cancel() }
                    scope.cancel()
                    withContext(NonCancellable) {
                        withTimeoutOrNull(OPERATION_JOIN_TIMEOUT_MILLIS) { jobs.joinAll() }
                        resources.close()
                    }
                } finally {
                    scope.cancel()
                }
            }
            cleanup.invokeOnCompletion { cleanupParent.cancel() }
            leaveJob = cleanup
            cleanup.start()
            cleanup
        }
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private const val OPERATION_JOIN_TIMEOUT_MILLIS = 1_000L

        /** Normalizes pasted input to exactly the alphabet used by generated room codes. */
        fun normalizeRoomCodeInput(value: String): String = value.uppercase().filter { it in ALPHABET }.take(5)

        fun randomCode(len: Int): String {
            val r = SecureRandom()
            return buildString { repeat(len) { append(ALPHABET[r.nextInt(ALPHABET.length)]) } }
        }
    }
}
