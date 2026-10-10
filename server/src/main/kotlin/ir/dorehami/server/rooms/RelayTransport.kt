package ir.dorehami.server.rooms

import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The server side of one room's pipe. Each connected player's socket is attached as a *sink*; what
 * the socket receives is pushed in with [inbound]. The [ir.dorehami.session.HostController] running
 * on the server sees an ordinary [GameTransport] — identical to Bluetooth from its point of view.
 *
 * Identity: the peer id is the authenticated player id from the ticket, never anything in the message.
 */
class RelayTransport(override val localPeer: String = "authority") : GameTransport {
    private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    private val sinks = ConcurrentHashMap<String, suspend (String) -> Unit>()

    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    val connectedPeers: Set<String> get() = sinks.keys

    /** A socket for [peer] is live. Replaces a previous one (reconnect). */
    fun attach(peer: String, sink: suspend (String) -> Unit) {
        sinks[peer] = sink
        inbox.trySend(TransportEvent.PeerConnected(peer))
    }

    /** Only detaches if [sink] is still the current one (a newer socket may have replaced it). */
    fun detach(peer: String, sink: (suspend (String) -> Unit)? = null) {
        val cur = sinks[peer] ?: return
        if (sink != null && cur !== sink) return
        sinks.remove(peer)
        inbox.trySend(TransportEvent.PeerDisconnected(peer))
    }

    fun inbound(peer: String, text: String) {
        inbox.trySend(TransportEvent.Message(peer, text))
    }

    override suspend fun send(to: String?, text: String) {
        if (to != null) {
            sinks[to]?.let { runCatching { it(text) } }
        } else {
            for ((_, s) in sinks) runCatching { s(text) }
        }
    }

    override suspend fun close() {
        sinks.clear()
        inbox.trySend(TransportEvent.Closed)
        inbox.close()
    }
}
