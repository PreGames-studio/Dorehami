package ir.dorehami.transport

import kotlinx.coroutines.flow.Flow

/** What comes out of a pipe. */
sealed interface TransportEvent {
    data class PeerConnected(val peer: String) : TransportEvent
    data class PeerDisconnected(val peer: String) : TransportEvent
    data class Message(val from: String, val text: String) : TransportEvent
    data object Closed : TransportEvent
}

/**
 * A room-scoped pipe: text messages between peers. Bluetooth (Nearby Connections), the WebSocket
 * room socket and the in-memory test hub all implement exactly this — the session layer cannot tell
 * them apart (ADR-0004).
 *
 * Framing/fragmentation is the transport's job; the session only sees whole messages.
 */
interface GameTransport {
    /** My id on this pipe (the host's peer id is also the room authority id). */
    val localPeer: String

    /** Events in arrival order. Collect once. */
    val events: Flow<TransportEvent>

    /** Sends [text] to [to], or to everybody connected when [to] is `null`. */
    suspend fun send(to: String?, text: String)

    suspend fun close()
}
