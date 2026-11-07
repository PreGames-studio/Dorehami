package ir.dorehami.games.esmfamil.ui

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.esmfamil.EsmFamilModule
import ir.dorehami.games.esmfamil.EsmFamilSession
import ir.dorehami.games.esmfamil.EsmFamilSheet
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.SessionClient
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.transport.nearby.NearbyTransport
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.security.SecureRandom

/** Where a Bluetooth اسم فامیل table is, from the first tap to the sheet. */
sealed interface EsmFamilRoomStage {
    /** Host or join. */
    data object Choose : EsmFamilRoomStage

    data class Connecting(val message: String) : EsmFamilRoomStage

    /** Guest side: the rooms this phone can see. */
    data object Discovering : EsmFamilRoomStage

    data class Lobby(
        val client: SessionClient,
        val code: String,
        val isHost: Boolean,
        val members: Int,
        val seats: Int,
        val hint: String,
    ) : EsmFamilRoomStage

    data class Playing(val client: SessionClient) : EsmFamilRoomStage

    data class Failed(val message: String) : EsmFamilRoomStage
}

/**
 * The Bluetooth table of اسم فامیل.
 *
 * Same shape as the other games' rooms, and deliberately copied rather than shared: a game module
 * may not depend on another game (`docs/ARCHITECTURE.md`). The host phone runs the real
 * [ir.dorehami.gamesdk.GameEngine], so a Bluetooth round is scored by exactly the same code as the
 * pass-and-play one.
 *
 * اسم فامیل has no bots, so a seat that never fills simply writes nothing: the host closes the
 * round (`finish_round`) as soon as every human sheet is in, and the empty seat takes zero points.
 */
class EsmFamilRoomFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerName: String,
    private val rounds: Int,
    private val categories: List<String>,
    private val letters: String,
    private val seatCount: Int,
) {
    private companion object {
        const val LINK_TIMEOUT_MS = 20_000L
        const val RECONNECT_TIMEOUT_MS = 12_000L
        const val BLUETOOTH_RETRIES = 3

        /** Same alphabet the other rooms use, so a code read aloud is never ambiguous. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomCode(length: Int): String {
            val random = SecureRandom()
            return buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }
    }

    private val _stage = mutableStateOf<EsmFamilRoomStage>(EsmFamilRoomStage.Choose)
    val stage: State<EsmFamilRoomStage> get() = _stage

    val me = PlayerInfo(PlayerId("p_" + randomCode(8).lowercase()), playerName.ifBlank { "بازیکن" })

    private val options = view {
        put(EsmFamilSession.Options.ROUNDS, rounds)
        put(
            EsmFamilSession.Options.CATEGORIES,
            buildJsonArray { categories.forEach { add(JsonPrimitive(it)) } },
        )
        put(EsmFamilSession.Options.LETTERS, letters)
    }

    private var nearby: NearbyTransport? = null
    private var client: SessionClient? = null
    private var watcher: Job? = null
    private var linkWatcher: Job? = null
    private var closed = false

    val nearbyRooms: StateFlow<List<NearbyRoom>>? get() = nearby?.discovered

    // ------------------------------------------------------------------ host

    fun host() = scope.launch {
        closed = false
        var connected = false
        try {
            _stage.value = EsmFamilRoomStage.Connecting("در حال ساخت میز…")
            val code = randomCode(5)
            val transport = NearbyTransport(context, localPeer = "host")
            nearby = transport
            val host = HostController(
                module = EsmFamilModule,
                transport = transport,
                scope = scope,
                roomCode = code,
                roomName = "میز ${me.displayName}",
                hostPlayer = me,
                options = options,
                seed = SecureRandom().nextLong(),
                clock = { System.currentTimeMillis() },
                rngFactory = { SplitMixRng(it) },
                partySize = seatCount,
                botDelayMs = 0L,
                botTakeoverOnDisconnect = false,
            )
            host.open()
            client = host
            if (!transport.advertise(code, me.displayName)) {
                _stage.value = EsmFamilRoomStage.Failed(
                    "بلوتوث در دسترس نیست. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.",
                )
                return@launch
            }
            _stage.value = EsmFamilRoomStage.Lobby(
                client = host,
                code = code,
                isHost = true,
                members = 1,
                seats = seatCount,
                hint = "بازی وقتی شروع می‌شود که بزنی؛ صندلی‌های خالی برگهٔ خالی می‌گیرند.",
            )
            watch(host)
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    // ------------------------------------------------------------------ guest

    fun discover() = scope.launch {
        closed = false
        var connected = false
        try {
            _stage.value = EsmFamilRoomStage.Connecting("در حال جست‌وجوی میزهای اطراف…")
            val transport = NearbyTransport(context, localPeer = "guest")
            nearby = transport
            if (!transport.discover()) {
                _stage.value = EsmFamilRoomStage.Failed(
                    "جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.",
                )
                return@launch
            }
            _stage.value = EsmFamilRoomStage.Discovering
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    fun join(room: NearbyRoom) = scope.launch {
        closed = false
        val transport = nearby ?: return@launch
        var connected = false
        try {
            _stage.value = EsmFamilRoomStage.Connecting("در حال اتصال به میز ${room.roomCode}…")
            transport.stopDiscovery()
            if (!transport.connectTo(room, me.displayName)) {
                _stage.value = EsmFamilRoomStage.Failed("میزبان اتصال را نپذیرفت. نزدیک‌تر بیا و دوباره تلاش کن.")
                return@launch
            }
            val guest = GuestController(
                transport,
                scope,
                roomCode = room.roomCode,
                hostPeer = room.endpointId,
                player = me,
                nowMs = { System.currentTimeMillis() },
            )
            client = guest
            guest.join()
            // `lobby.ack` proves the link, not just that requestConnection() returned.
            val state = withTimeoutOrNull(LINK_TIMEOUT_MS) { guest.state.first { it.room != null } }
            if (state == null) {
                _stage.value = EsmFamilRoomStage.Failed("اتصال برقرار نشد. نزدیک‌تر بیا و دوباره تلاش کن.")
                return@launch
            }
            _stage.value = EsmFamilRoomStage.Lobby(
                client = guest,
                code = room.roomCode,
                isHost = false,
                members = state.room?.members?.size ?: 1,
                seats = state.room?.members?.size ?: 1,
                hint = "منتظر میزبان بمان؛ وقتی «شروع» را بزند برگه‌ها باز می‌شوند.",
            )
            watch(guest)
            watchLink(transport, room, guest)
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    /** The host starts the match; both sides move to [EsmFamilRoomStage.Playing]. */
    fun start() {
        val c = client ?: return
        scope.launch {
            runCatching {
                when (c) {
                    is HostController -> if (!c.startMatch()) {
                        _stage.value = EsmFamilRoomStage.Connecting("شروع انجام نشد؛ دوباره تلاش کن.")
                    }
                    is GuestController -> c.requestStart()
                    else -> Unit
                }
            }.onFailure {
                _stage.value = EsmFamilRoomStage.Failed("شروع بازی انجام نشد. اتصال را چک کن و دوباره تلاش کن.")
            }
        }
    }

    fun leave() = teardown()

    // ------------------------------------------------------------------ internals

    private fun watch(c: SessionClient) {
        watcher?.cancel()
        watcher = scope.launch {
            c.state.collect { state ->
                if (state.room?.started == true) {
                    if (_stage.value !is EsmFamilRoomStage.Playing) {
                        nearby?.stopAdvertising()
                        _stage.value = EsmFamilRoomStage.Playing(c)
                    }
                } else {
                    val lobby = _stage.value as? EsmFamilRoomStage.Lobby ?: return@collect
                    _stage.value = lobby.copy(members = state.room?.members?.size ?: lobby.members)
                }
            }
        }
    }

    /** Keeps a dropped Bluetooth link alive mid-match: the guest rejoins and the host resends state. */
    private fun watchLink(transport: NearbyTransport, room: NearbyRoom, guest: GuestController) {
        linkWatcher?.cancel()
        linkWatcher = scope.launch {
            var reconnecting = false
            guest.state.collect { state ->
                if (reconnecting || state.connected || state.room == null) return@collect
                reconnecting = true
                launch {
                    var attempt = 0
                    while (attempt < BLUETOOTH_RETRIES && !guest.state.value.connected) {
                        attempt++
                        if (_stage.value !is EsmFamilRoomStage.Playing) {
                            _stage.value = EsmFamilRoomStage.Connecting("ارتباط قطع شد · تلاش $attempt…")
                        }
                        runCatching { transport.connectTo(room, me.displayName) }
                        withTimeoutOrNull(RECONNECT_TIMEOUT_MS) { guest.state.first { it.connected } }
                    }
                    reconnecting = false
                    if (!guest.state.value.connected && _stage.value !is EsmFamilRoomStage.Playing) {
                        _stage.value = EsmFamilRoomStage.Failed("ارتباط قطع شد. نزدیک‌تر شو و دوباره وصل کن.")
                    }
                }
            }
        }
    }

    /**
     * Releases everything for the current link, exactly once, on its own job so the cleanup outlives
     * the screen's scope (the other rooms do the same for the same reason).
     */
    private fun teardown() {
        if (closed) return
        closed = true
        watcher?.cancel()
        linkWatcher?.cancel()
        watcher = null
        linkWatcher = null
        val c = client
        val t = nearby
        client = null
        nearby = null
        val job: CompletableJob = Job()
        CoroutineScope(job + Dispatchers.Main.immediate).launch {
            try {
                runCatching { c?.leave() }
                runCatching { t?.close() }
            } finally {
                job.complete()
            }
        }
    }
}
