package ir.dorehami.transport.online

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One room socket to the server. The server is the only peer and is always called [SERVER_PEER], so
 * a [ir.dorehami.session.GuestController] built with `hostPeer = SERVER_PEER` works unchanged.
 *
 * Reconnects with backoff when the link drops (mobile data in Iran drops a lot): emits
 * [TransportEvent.PeerDisconnected] then [TransportEvent.PeerConnected] again, which makes the guest
 * controller re-join and the server resync it.
 */
class WebSocketTransport(
    private val client: HttpClient,
    private val wsUrl: String,
    private val scope: CoroutineScope,
    override val localPeer: String,
    private val maxRetries: Int = 8,
) : GameTransport {
    companion object {
        const val SERVER_PEER = "authority"
    }

    private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    @Volatile private var closed = false

    /** Opens the socket and starts the reader; returns once the first connection is up (or throws). */
    suspend fun connect() {
        val s = client.webSocketSession(wsUrl)
        session = s
        inbox.trySend(TransportEvent.PeerConnected(SERVER_PEER))
        job = scope.launch { pump(s) }
    }

    private suspend fun pump(first: DefaultClientWebSocketSession) {
        var s = first
        var retries = 0
        while (!closed) {
            try {
                for (frame in s.incoming) {
                    if (frame is Frame.Text) inbox.trySend(TransportEvent.Message(SERVER_PEER, frame.readText()))
                }
            } catch (_: Throwable) {
                // fall through to reconnect
            }
            if (closed) break
            inbox.trySend(TransportEvent.PeerDisconnected(SERVER_PEER))
            var connected = false
            while (!closed && !connected && retries < maxRetries) {
                delay(minOf(500L shl retries, 10_000L))
                retries++
                try {
                    s = client.webSocketSession(wsUrl)
                    session = s
                    connected = true
                    retries = 0
                    inbox.trySend(TransportEvent.PeerConnected(SERVER_PEER))
                } catch (_: Throwable) {
                }
            }
            if (!connected) break
        }
        inbox.trySend(TransportEvent.Closed)
    }

    override suspend fun send(to: String?, text: String) {
        session?.send(Frame.Text(text))
    }

    override suspend fun close() {
        closed = true
        runCatching { session?.close() }
        job?.cancel()
        inbox.trySend(TransportEvent.Closed)
    }
}
