package ir.dorehami.server.rooms

import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class RoomException(val code: String, val messageFa: String) : RuntimeException(messageFa)

/** One live room: a headless [HostController] arbitrating over a [RelayTransport]. */
class ServerRoom(
    val code: String,
    val name: String,
    val module: GameModule,
    val partySize: Int,
    val options: JsonObject,
    val seed: Long,
    val createdAtMs: Long,
    val transport: RelayTransport,
    val controller: HostController,
) {
    val gameId: String get() = module.meta.id.value
    val started: Boolean get() = controller.isStarted
    val finished: Boolean get() = controller.state.value.result != null
    val memberCount: Int get() = controller.state.value.room?.members?.count { !it.isBot } ?: 0
}

/**
 * All rooms on this server. The server *is* the authority (docs/ARCHITECTURE.md): clients only ever
 * send actions and receive views, exactly like guests of a Bluetooth room.
 */
class RoomHub(
    private val registry: GameRegistry,
    private val scope: CoroutineScope,
    private val store: MatchStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val seedSource: () -> Long = { SecureRandom().nextLong() },
    private val codeSource: () -> String = { randomCode() },
    /** Pause before bot moves so humans can follow; tests pass 0. */
    private val botDelayMs: Long = 900,
    /** Finished/abandoned rooms are reaped after this. */
    private val roomTtlMs: Long = 30 * 60 * 1000L,
) {
    private val rooms = ConcurrentHashMap<String, ServerRoom>()

    fun get(code: String): ServerRoom? = rooms[code.uppercase()]

    fun all(): Collection<ServerRoom> = rooms.values

    fun create(gameId: String, partySize: Int?, options: JsonObject?, name: String): ServerRoom {
        val module = registry[ir.dorehami.protocol.GameId(gameId)]
            ?: throw RoomException(ErrCodes.PROTO_UNKNOWN_TYPE, "این بازی روی سرور نیست")
        val size = (partySize ?: module.preferredPartySize()).coerceIn(module.meta.minPlayers, module.meta.maxPlayers)
        val opts = try { module.validateOptions(options ?: module.defaultOptions()) } catch (e: ir.dorehami.gamesdk.GameConfigException) {
            throw RoomException(e.code, e.messageFa)
        }
        var code: String
        do { code = codeSource() } while (rooms.containsKey(code))
        val transport = RelayTransport()
        val seed = seedSource()
        val roomScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val controller = HostController(
            module = module, transport = transport, scope = roomScope, roomCode = code, roomName = name,
            hostPlayer = null, options = opts, seed = seed,
            clock = { nowMs() }, rngFactory = { SplitMixRng(it) },
            partySize = size, botDelayMs = botDelayMs,
            onFinished = { record -> store.save(code, record) },
        )
        controller.open()
        val room = ServerRoom(code, name, module, size, opts, seed, nowMs(), transport, controller)
        rooms[code] = room
        return room
    }

    /** A player's socket is up: bind it. The lobby.join message itself comes through the socket. */
    fun attach(code: String, player: PlayerInfo, sink: suspend (String) -> Unit): ServerRoom {
        val room = get(code) ?: throw RoomException(ErrCodes.ROOM_NOT_FOUND, "اتاقی با این کد پیدا نشد")
        room.transport.attach(player.id.value, sink)
        return room
    }

    fun detach(code: String, player: PlayerId, sink: suspend (String) -> Unit) {
        get(code)?.transport?.detach(player.value, sink)
    }

    fun inbound(code: String, player: PlayerId, text: String) {
        get(code)?.transport?.inbound(player.value, text)
    }

    /** Removes rooms that finished or were abandoned long ago. Call periodically. */
    suspend fun reap() {
        val t = nowMs()
        for ((code, r) in rooms) {
            val idle = r.transport.connectedPeers.isEmpty() && t - r.createdAtMs > roomTtlMs
            if (r.finished || idle) {
                rooms.remove(code)
                r.controller.leave()
            }
        }
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O/1/I/L: read aloud on the phone

        fun randomCode(len: Int = 5, rnd: java.util.Random = SecureRandom()): String =
            buildString { repeat(len) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }
    }
}

/** Where finished matches go. In-memory by default; the Postgres version implements the same thing. */
interface MatchStore {
    fun save(roomCode: String, record: MatchRecord)
    fun get(roomCode: String): MatchRecord?
    fun recent(limit: Int = 50): List<Pair<String, MatchRecord>>
}

class InMemoryMatchStore(private val capacity: Int = 1000) : MatchStore {
    private val items = LinkedHashMap<String, MatchRecord>()

    @Synchronized
    override fun save(roomCode: String, record: MatchRecord) {
        items[roomCode] = record
        while (items.size > capacity) items.remove(items.keys.first())
    }

    @Synchronized
    override fun get(roomCode: String): MatchRecord? = items[roomCode]

    @Synchronized
    override fun recent(limit: Int): List<Pair<String, MatchRecord>> = items.entries.toList().takeLast(limit).reversed().map { it.key to it.value }
}
