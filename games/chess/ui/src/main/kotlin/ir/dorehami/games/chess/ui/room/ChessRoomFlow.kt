package ir.dorehami.games.chess.ui.room

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.chess.ChessModule
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.SessionClient
import ir.dorehami.session.SessionEvent
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import ir.dorehami.transport.hotspot.HotspotTransport
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.transport.nearby.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

/** Offline two-phone connection technology; neither mode uses the online server. */
enum class ChessRoomMode { BLUETOOTH, HOTSPOT }

/** Stages of a real offline room, from discovery to a host-authoritative chess match. */
sealed interface ChessRoomStage {
    data object Choose : ChessRoomStage
    data class Connecting(val message: String) : ChessRoomStage
    data object Discovering : ChessRoomStage
    data class Lobby(
        val client: SessionClient,
        val code: String,
        val canStart: Boolean,
        val hint: String,
        val hotspotAddress: String? = null,
    ) : ChessRoomStage
    data class Playing(val client: SessionClient, val mode: ChessRoomMode) : ChessRoomStage
    data class Failed(val message: String) : ChessRoomStage
}

/**
 * Wires chess to Nearby Connections or a direct hotspot socket and the shared session controllers.
 * Only the host applies chess actions; guests send actions and render their own private view.
 */
class ChessRoomFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    val mode: ChessRoomMode,
    private val playerName: () -> String,
) {
    private val _stage = mutableStateOf<ChessRoomStage>(ChessRoomStage.Choose)
    val stage: State<ChessRoomStage> get() = _stage

    private val _notice = mutableStateOf<String?>(null)
    val notice: State<String?> get() = _notice

    private var nearby: NearbyTransport? = null
    private var hotspot: HotspotTransport? = null
    private var client: SessionClient? = null
    private var startWatcher: Job? = null
    private var errorWatcher: Job? = null

    private val identity: PlayerInfo by lazy {
        PlayerInfo(
            PlayerId("p_" + randomCode(8).lowercase()),
            playerName().ifBlank { "بازیکن شطرنج" },
        )
    }

    val nearbyRooms: StateFlow<List<NearbyRoom>>? get() = nearby?.discovered

    fun hostBluetooth() = scope.launch {
        _stage.value = ChessRoomStage.Connecting("در حال ساخت اتاق بلوتوث…")
        _notice.value = null
        val code = randomCode(5)
        try {
            val transport = NearbyTransport(
                context = context,
                localPeer = HotspotTransport.HOST_PEER,
                serviceId = CHESS_NEARBY_SERVICE,
            ).also { nearby = it }
            val host = newHost(transport, code)
            client = host
            host.open()
            if (!transport.advertise(code, me().displayName)) {
                closeConnections()
                _stage.value = ChessRoomStage.Failed("اتاق ساخته نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌های Nearby را بده.")
                return@launch
            }
            _stage.value = ChessRoomStage.Lobby(
                client = host,
                code = code,
                canStart = true,
                hint = "دوستت در بخش بلوتوث «پیوستن» را بزند و این اتاق را انتخاب کند.",
            )
            watchStart(host)
        } catch (_: Exception) {
            closeConnections()
            _stage.value = ChessRoomStage.Failed("بلوتوث در دسترس نیست. رادیو و اجازه‌ها را بررسی کن و دوباره امتحان کن.")
        }
    }

    fun joinBluetooth() = scope.launch {
        _stage.value = ChessRoomStage.Connecting("در حال جست‌وجوی اتاق‌های شطرنج…")
        _notice.value = null
        try {
            val transport = NearbyTransport(
                context = context,
                localPeer = "guest",
                serviceId = CHESS_NEARBY_SERVICE,
            ).also { nearby = it }
            if (!transport.discover()) {
                closeConnections()
                _stage.value = ChessRoomStage.Failed("جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.")
                return@launch
            }
            _stage.value = ChessRoomStage.Discovering
        } catch (_: Exception) {
            closeConnections()
            _stage.value = ChessRoomStage.Failed("جست‌وجوی بلوتوث انجام نشد. اجازه‌ها را بررسی کن.")
        }
    }

    fun connectBluetooth(room: NearbyRoom) = scope.launch {
        val transport = nearby ?: return@launch
        _stage.value = ChessRoomStage.Connecting("در حال اتصال به اتاق ${room.roomCode}…")
        val accepted = scope.launch {
            transport.events.first { it is TransportEvent.PeerConnected && it.peer == room.endpointId }
        }
        try {
            transport.stopDiscovery()
            if (!transport.connectTo(room, me().displayName)) {
                accepted.cancel()
                closeConnections()
                _stage.value = ChessRoomStage.Failed("میزبان اتصال را نپذیرفت.")
                return@launch
            }
            val connected = withTimeoutOrNull(20_000) { accepted.join(); true } ?: false
            if (!connected) {
                accepted.cancel()
                closeConnections()
                _stage.value = ChessRoomStage.Failed("اتصال برقرار نشد. گوشی‌ها را نزدیک‌تر کن و دوباره امتحان کن.")
                return@launch
            }
            completeGuestJoin(
                transport = transport,
                code = room.roomCode,
                hostPeer = room.endpointId,
                hint = "منتظر شروع از طرف میزبان بمان.",
            )
        } catch (_: Exception) {
            accepted.cancel()
            closeConnections()
            _stage.value = ChessRoomStage.Failed("اتصال بلوتوث قطع شد. دوباره تلاش کن.")
        }
    }

    fun hostHotspot() = scope.launch {
        _stage.value = ChessRoomStage.Connecting("در حال بازکردن اتاق روی شبکهٔ محلی…")
        _notice.value = null
        try {
            val transport = HotspotTransport.host().also { hotspot = it }
            val host = newHost(transport, HOTSPOT_ROOM_CODE)
            client = host
            host.open()
            val addresses = HotspotTransport.localIPv4Addresses()
            val addressText = if (addresses.isEmpty()) {
                "IP دستگاه را از تنظیمات هات‌اسپات پیدا کن"
            } else {
                addresses.joinToString(" · ")
            }
            _stage.value = ChessRoomStage.Lobby(
                client = host,
                code = HOTSPOT_ROOM_CODE,
                canStart = true,
                hint = "روی گوشی دوم همین IP و پورت را وارد کن. هر دو گوشی باید روی یک هات‌اسپات باشند.",
                hotspotAddress = "$addressText\nپورت: ${transport.boundPort}",
            )
            watchStart(host)
        } catch (_: Exception) {
            closeConnections()
            _stage.value = ChessRoomStage.Failed("پورت محلی باز نشد. هات‌اسپات را روشن کن و دوباره تلاش کن.")
        }
    }

    fun joinHotspot(address: String, portText: String) = scope.launch {
        val hostAddress = HotspotTransport.normalizeHostAddress(address)
        val port = portText.trim().toIntOrNull()
        if (hostAddress.isEmpty() || port == null || port !in 1..65535) {
            _notice.value = "IP و پورت معتبر وارد کن؛ پورت پیش‌فرض ${HotspotTransport.DEFAULT_PORT} است."
            return@launch
        }
        _stage.value = ChessRoomStage.Connecting("در حال اتصال مستقیم به میزبان…")
        _notice.value = null
        try {
            val transport = HotspotTransport.join(hostAddress, port).also { hotspot = it }
            completeGuestJoin(
                transport = transport,
                code = HOTSPOT_ROOM_CODE,
                hostPeer = HotspotTransport.HOST_PEER,
                hint = "اتصال محلی برقرار شد؛ منتظر شروع از طرف میزبان بمان.",
            )
        } catch (_: Exception) {
            closeConnections()
            _stage.value = ChessRoomStage.Failed("میزبان پیدا نشد. IP و پورت، روشن‌بودن هات‌اسپات و اتصال هر دو گوشی را بررسی کن.")
        }
    }

    private suspend fun completeGuestJoin(
        transport: GameTransport,
        code: String,
        hostPeer: String,
        hint: String,
    ) {
        val guest = GuestController(
            transport = transport,
            scope = scope,
            roomCode = code,
            hostPeer = hostPeer,
            player = me(),
            nowMs = { System.currentTimeMillis() },
        )
        client = guest
        watchErrors(guest)
        guest.join()
        val joined = withTimeoutOrNull(10_000) { guest.state.first { it.room != null } }
        if (joined == null || _stage.value is ChessRoomStage.Failed) {
            if (_stage.value !is ChessRoomStage.Failed) {
                _stage.value = ChessRoomStage.Failed("میزبان به درخواست عضویت جواب نداد.")
            }
            closeConnections()
            return
        }
        _stage.value = ChessRoomStage.Lobby(guest, code, canStart = false, hint = hint)
        watchStart(guest)
    }

    /** Starts only when two human seats are present; chess rooms never silently replace a guest with a bot. */
    fun start() = scope.launch {
        val host = client as? HostController ?: return@launch
        val humans = host.state.value.room?.members?.count { !it.isBot } ?: 0
        if (humans != 2) {
            _notice.value = "برای شروع، بازیکن دوم باید به اتاق وصل شود."
            return@launch
        }
        if (!host.startMatch()) _notice.value = "شروع انجام نشد؛ وضعیت اتاق را بررسی کن."
    }

    fun setReady(ready: Boolean) = scope.launch { client?.ready(ready) }

    fun clearNotice() {
        _notice.value = null
    }

    private fun newHost(transport: GameTransport, code: String): HostController = HostController(
        module = ChessModule,
        transport = transport,
        scope = scope,
        roomCode = code,
        roomName = "اتاق شطرنج ${me().displayName}",
        hostPlayer = me(),
        options = ChessModule.defaultOptions(),
        seed = SecureRandom().nextLong(),
        clock = { System.currentTimeMillis() },
        rngFactory = { SplitMixRng(it) },
        partySize = 2,
        botDelayMs = 0,
        botTakeoverOnDisconnect = false,
    )

    private fun watchStart(session: SessionClient) {
        startWatcher?.cancel()
        startWatcher = scope.launch {
            session.state.first { it.room?.started == true }
            nearby?.stopAdvertising()
            _stage.value = ChessRoomStage.Playing(session, mode)
        }
    }

    private fun watchErrors(session: SessionClient) {
        errorWatcher?.cancel()
        errorWatcher = scope.launch {
            session.events.collect { event ->
                if (event is SessionEvent.Error && _stage.value !is ChessRoomStage.Playing) {
                    _stage.value = ChessRoomStage.Failed(event.messageFa)
                }
            }
        }
    }

    fun leave() {
        startWatcher?.cancel()
        errorWatcher?.cancel()
        scope.launch { closeConnections() }
    }

    private suspend fun closeConnections() {
        runCatching { client?.leave() }
        runCatching { nearby?.close() }
        runCatching { hotspot?.close() }
        client = null
        nearby = null
        hotspot = null
    }

    private fun me(): PlayerInfo = identity

    companion object {
        private const val CHESS_NEARBY_SERVICE = "ir.dorehami.chess"
        private const val HOTSPOT_ROOM_CODE = "CHESS"
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        private fun randomCode(length: Int): String {
            val random = SecureRandom()
            return buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }
    }
}
