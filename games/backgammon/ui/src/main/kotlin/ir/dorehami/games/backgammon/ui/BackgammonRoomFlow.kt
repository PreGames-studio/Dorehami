package ir.dorehami.games.backgammon.ui

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.backgammon.BackgammonModule
import ir.dorehami.games.backgammon.BackgammonSession
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
import kotlinx.serialization.json.put
import java.security.SecureRandom

/** Where a Bluetooth backgammon table is, from the first tap to the board. */
sealed interface BackgammonRoomStage {
    /** Host or join. */
    data object Choose : BackgammonRoomStage

    data class Connecting(val message: String) : BackgammonRoomStage

    /** Guest side: the rooms this phone can see. */
    data object Discovering : BackgammonRoomStage

    data class Lobby(
        val client: SessionClient,
        val code: String,
        val isHost: Boolean,
        val members: Int,
        val hint: String,
    ) : BackgammonRoomStage

    data class Playing(val client: SessionClient) : BackgammonRoomStage

    data class Failed(val message: String) : BackgammonRoomStage
}

/**
 * Backgammon's Bluetooth room.
 *
 * Backgammon is a two-seat game, so the whole flow is one host and one guest on Nearby Connections;
 * the host runs the same [ir.dorehami.gamesdk.GameEngine] the offline table runs, so the rules on a
 * Bluetooth board are byte-for-byte the offline ones. This file is the backgammon equivalent of the
 * hokm room flow — copied rather than shared, because a game module may not depend on another game
 * (the golden rule in `docs/ARCHITECTURE.md`).
 */
class BackgammonRoomFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerName: String,
    private val targetPoints: Int,
) {
    private companion object {
        const val LINK_TIMEOUT_MS = 20_000L
        const val RECONNECT_TIMEOUT_MS = 12_000L
        const val BLUETOOTH_RETRIES = 3

        /** Same alphabet the hokm room uses, so a code read aloud can never be ambiguous. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomCode(length: Int): String {
            val random = SecureRandom()
            return buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }
    }

    private val _stage = mutableStateOf<BackgammonRoomStage>(BackgammonRoomStage.Choose)
    val stage: State<BackgammonRoomStage> get() = _stage

    val me = PlayerInfo(PlayerId("p_" + randomCode(8).lowercase()), playerName.ifBlank { "بازیکن" })
    private val options = view { put(BackgammonSession.Options.TARGET_POINTS, targetPoints) }

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
            _stage.value = BackgammonRoomStage.Connecting("در حال ساخت میز…")
            val code = randomCode(5)
            val transport = NearbyTransport(context, localPeer = "host")
            nearby = transport
            // Two seats: the host plays white, the guest plays black. No bots on a Bluetooth board.
            val host = HostController(
                module = BackgammonModule,
                transport = transport,
                scope = scope,
                roomCode = code,
                roomName = "میز ${me.displayName}",
                hostPlayer = me,
                options = options,
                seed = SecureRandom().nextLong(),
                clock = { System.currentTimeMillis() },
                rngFactory = { SplitMixRng(it) },
                partySize = 2,
                botDelayMs = 0L,
                botTakeoverOnDisconnect = false,
            )
            host.open()
            client = host
            if (!transport.advertise(code, me.displayName)) {
                _stage.value = BackgammonRoomStage.Failed(
                    "بلوتوث در دسترس نیست. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.",
                )
                return@launch
            }
            _stage.value = BackgammonRoomStage.Lobby(
                client = host,
                code = code,
                isHost = true,
                members = 1,
                hint = "حریف باید «پیوستن» را بزند و میز تو را انتخاب کند. وقتی وصل شد، «شروع» را بزن.",
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
            _stage.value = BackgammonRoomStage.Connecting("در حال جست‌وجوی میزهای اطراف…")
            val transport = NearbyTransport(context, localPeer = "guest")
            nearby = transport
            if (!transport.discover()) {
                _stage.value = BackgammonRoomStage.Failed(
                    "جست‌وجو شروع نشد. بلوتوث و موقعیت مکانی را روشن کن و اجازه‌ها را بده.",
                )
                return@launch
            }
            _stage.value = BackgammonRoomStage.Discovering
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
            _stage.value = BackgammonRoomStage.Connecting("در حال اتصال به میز ${room.roomCode}…")
            transport.stopDiscovery()
            if (!transport.connectTo(room, me.displayName)) {
                _stage.value = BackgammonRoomStage.Failed("میزبان اتصال را نپذیرفت. نزدیک‌تر بیا و دوباره تلاش کن.")
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
                _stage.value = BackgammonRoomStage.Failed("اتصال برقرار نشد. نزدیک‌تر بیا و دوباره تلاش کن.")
                return@launch
            }
            _stage.value = BackgammonRoomStage.Lobby(
                client = guest,
                code = room.roomCode,
                isHost = false,
                members = state.room?.members?.size ?: 1,
                hint = "منتظر میزبان بمان؛ وقتی «شروع» را بزند تخته باز می‌شود.",
            )
            watch(guest)
            watchLink(transport, room, guest)
            connected = true
        } finally {
            if (!connected) teardown()
        }
    }

    /** The host starts the match; both sides move to [BackgammonRoomStage.Playing]. */
    fun start() {
        val c = client ?: return
        // Two seats, no bots: `HostController.startMatch()` fills empty seats with bots, so starting
        // alone would put a robot across the Bluetooth board. Refuse instead of doing that.
        val members = c.state.value.room?.members?.size ?: 1
        if (c is HostController && members < 2) return
        scope.launch {
            runCatching {
                // Host opens the table; a guest can only nudge the host to begin.
                when (c) {
                    is HostController -> if (!c.startMatch()) {
                        _stage.value = BackgammonRoomStage.Connecting("شروع انجام نشد؛ دوباره تلاش کن.")
                    }
                    is GuestController -> c.requestStart()
                    else -> Unit
                }
            }.onFailure {
                _stage.value = BackgammonRoomStage.Failed("شروع بازی انجام نشد. اتصال را چک کن و دوباره تلاش کن.")
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
                    if (_stage.value !is BackgammonRoomStage.Playing) {
                        nearby?.stopAdvertising()
                        _stage.value = BackgammonRoomStage.Playing(c)
                    }
                } else {
                    val lobby = _stage.value as? BackgammonRoomStage.Lobby ?: return@collect
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
                        if (_stage.value !is BackgammonRoomStage.Playing) {
                            _stage.value = BackgammonRoomStage.Connecting("ارتباط قطع شد · تلاش $attempt…")
                        }
                        runCatching { transport.connectTo(room, me.displayName) }
                        withTimeoutOrNull(RECONNECT_TIMEOUT_MS) { guest.state.first { it.connected } }
                    }
                    reconnecting = false
                    if (!guest.state.value.connected && _stage.value !is BackgammonRoomStage.Playing) {
                        _stage.value = BackgammonRoomStage.Failed("ارتباط قطع شد. نزدیک‌تر شو و دوباره وصل کن.")
                    }
                }
            }
        }
    }

    /**
     * Releases everything for the current link, exactly once.
     *
     * `BackgammonBluetoothRoom` cancels its scope right after `leave()`, and `leave()`/`close()` are
     * suspend calls, so the cleanup runs on its own short-lived Job that outlives the caller's scope.
     * Fields are cleared synchronously, so a second call can never touch a half-closed client.
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
