package ir.dorehami.testkit

import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * An in-memory "room": every transport created from the same hub is connected to every other one.
 * Deterministic, no threads, no sockets — ideal for controller tests.
 */
class FakeHub {
    private val peers = LinkedHashMap<String, FakeTransport>()

    fun connect(peerId: String): FakeTransport {
        val t = FakeTransport(peerId, this)
        for (other in peers.values) {
            other.inbox.trySend(TransportEvent.PeerConnected(peerId))
            t.inbox.trySend(TransportEvent.PeerConnected(other.localPeer))
        }
        peers[peerId] = t
        return t
    }

    internal fun deliver(from: String, to: String?, text: String) {
        if (to != null) {
            peers[to]?.inbox?.trySend(TransportEvent.Message(from, text))
        } else {
            peers.values.filter { it.localPeer != from }.forEach { it.inbox.trySend(TransportEvent.Message(from, text)) }
        }
    }

    internal fun leave(peerId: String) {
        peers.remove(peerId)?.inbox?.trySend(TransportEvent.Closed)
        peers.values.forEach { it.inbox.trySend(TransportEvent.PeerDisconnected(peerId)) }
    }

    /** Simulates a dropped link without a clean close (phone went out of range). */
    fun drop(peerId: String) = leave(peerId)
}

class FakeTransport internal constructor(override val localPeer: String, private val hub: FakeHub) : GameTransport {
    internal val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    override suspend fun send(to: String?, text: String) = hub.deliver(localPeer, to, text)

    override suspend fun close() = hub.leave(localPeer)
}
