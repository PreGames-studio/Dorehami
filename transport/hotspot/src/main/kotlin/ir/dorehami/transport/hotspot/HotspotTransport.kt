package ir.dorehami.transport.hotspot

import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * A direct, server-free [GameTransport] for devices on the same hotspot or local Wi-Fi network.
 *
 * The host listens on `0.0.0.0`; a guest connects to the IPv4 address and port shown by the host.
 * Every message is framed as a big-endian byte length followed by UTF-8 data, so JSON envelopes
 * arrive whole even when TCP splits or coalesces packets. No internet route, DNS or relay is used.
 */
class HotspotTransport private constructor(
    override val localPeer: String,
    private val serverSocket: ServerSocket?,
) : GameTransport {
    companion object {
        const val DEFAULT_PORT = 47_823
        const val HOST_PEER = "host"
        private const val MAX_FRAME_BYTES = 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 10_000

        /** Opens a host socket. Pass `port = 0` in tests to ask the OS for a free port. */
        suspend fun host(port: Int = DEFAULT_PORT, localPeer: String = HOST_PEER): HotspotTransport =
            withContext(Dispatchers.IO) {
                require(port in 0..65535) { "Port must be between 0 and 65535" }
                val server = ServerSocket()
                try {
                    server.reuseAddress = true
                    server.bind(InetSocketAddress("0.0.0.0", port))
                    HotspotTransport(localPeer, server).also { it.acceptLoop() }
                } catch (error: Throwable) {
                    runCatching { server.close() }
                    throw error
                }
            }

        /** Connects a guest directly to [hostAddress] without contacting any external server. */
        suspend fun join(
            hostAddress: String,
            port: Int = DEFAULT_PORT,
            localPeer: String = "guest",
        ): HotspotTransport = withContext(Dispatchers.IO) {
            val address = normalizeHostAddress(hostAddress)
            require(address.isNotEmpty()) { "Host address is empty" }
            require(port in 1..65535) { "Port must be between 1 and 65535" }
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.tcpNoDelay = true
                socket.keepAlive = true
                HotspotTransport(localPeer, null).also { transport ->
                    transport.addConnection(HOST_PEER, socket)
                }
            } catch (error: Throwable) {
                runCatching { socket.close() }
                throw error
            }
        }

        /** Trims user input and accepts the bracketed form commonly used for IPv6 literals. */
        fun normalizeHostAddress(value: String): String = value.trim().let { address ->
            if (address.length > 2 && address.startsWith('[') && address.endsWith(']')) {
                address.substring(1, address.length - 1)
            } else {
                address
            }
        }

        /** Site-local IPv4 addresses that a second phone can enter (hotspot addresses included). */
        fun localIPv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { network ->
                val usable = runCatching { network.isUp && !network.isLoopback }.getOrDefault(false)
                if (!usable) return@flatMap emptyList<String>()
                network.inetAddresses.toList().mapNotNull { address ->
                    (address as? Inet4Address)
                        ?.takeIf { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        ?.hostAddress
                }
            }.distinct().sorted()
        }.getOrDefault(emptyList())
    }

    private data class Connection(
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val writeLock: Mutex = Mutex(),
    )

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    private val connections = LinkedHashMap<String, Connection>()
    private val connectionLock = Mutex()
    private val nextGuestId = AtomicInteger(1)

    @Volatile
    private var closed = false

    /** Actual bound port; useful when [host] was called with port zero. */
    val boundPort: Int get() = serverSocket?.localPort ?: -1

    private fun acceptLoop() {
        val server = serverSocket ?: return
        scope.launch {
            try {
                while (coroutineContext.isActive && !closed) {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    val peer = "guest-${nextGuestId.getAndIncrement()}"
                    addConnection(peer, socket)
                }
            } catch (_: IOException) {
                // Closing the server is the normal way to unblock accept().
                if (!closed) inbox.trySend(TransportEvent.Closed)
            }
        }
    }

    private suspend fun addConnection(peer: String, socket: Socket) {
        if (closed) {
            runCatching { socket.close() }
            return
        }
        val connection = Connection(socket, DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()))
        val registered = connectionLock.withLock {
            if (closed) false else {
                connections[peer] = connection
                true
            }
        }
        if (!registered) {
            runCatching { socket.close() }
            return
        }
        inbox.trySend(TransportEvent.PeerConnected(peer))
        scope.launch { readLoop(peer, connection) }
    }

    private suspend fun readLoop(peer: String, connection: Connection) {
        try {
            while (coroutineContext.isActive && !closed) {
                val size = connection.input.readInt()
                if (size <= 0 || size > MAX_FRAME_BYTES) throw IOException("Invalid frame size: $size")
                val bytes = ByteArray(size)
                connection.input.readFully(bytes)
                inbox.trySend(TransportEvent.Message(peer, String(bytes, Charsets.UTF_8)))
            }
        } catch (_: EOFException) {
            // A remote close is reported uniformly as PeerDisconnected below.
        } catch (_: IOException) {
            // Socket loss/reset has the same lifecycle meaning as an orderly remote close.
        } finally {
            var removed = false
            connectionLock.withLock {
                removed = connections.remove(peer) === connection
            }
            runCatching { connection.socket.close() }
            if (removed && !closed) inbox.trySend(TransportEvent.PeerDisconnected(peer))
        }
    }

    override suspend fun send(to: String?, text: String) {
        if (closed) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) {
            throw IllegalArgumentException("Transport frame must contain 1..$MAX_FRAME_BYTES bytes")
        }
        val targets = connectionLock.withLock {
            if (to == null) connections.values.toList() else listOfNotNull(connections[to])
        }
        for (connection in targets) {
            connection.writeLock.withLock {
                if (closed || connection.socket.isClosed) return@withLock
                try {
                    connection.output.writeInt(bytes.size)
                    connection.output.write(bytes)
                    connection.output.flush()
                } catch (_: IOException) {
                    runCatching { connection.socket.close() }
                }
            }
        }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        runCatching { serverSocket?.close() }
        val open = connectionLock.withLock {
            val copy = connections.values.toList()
            connections.clear()
            copy
        }
        open.forEach { connection -> runCatching { connection.socket.close() } }
        scope.cancel()
        inbox.trySend(TransportEvent.Closed)
    }
}
