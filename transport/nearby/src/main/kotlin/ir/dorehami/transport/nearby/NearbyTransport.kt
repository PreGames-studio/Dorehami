package ir.dorehami.transport.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import ir.dorehami.transport.Frames
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A room seen while discovering. */
data class NearbyRoom(val endpointId: String, val name: String, val roomCode: String)

/**
 * Offline rooms over Nearby Connections, `P2P_STAR`: the host advertises and is the hub; guests
 * discover and connect. Peer id = Nearby endpoint id. Messages go as `BYTES` payloads framed by
 * [Frames] so an envelope never hits the payload size limit, and arrive whole on the other side.
 *
 * Advertised name = `"<roomCode>|<hostName>"` so the lobby can show a code the host reads aloud.
 */
class NearbyTransport(
    context: Context,
    override val localPeer: String,
    private val serviceId: String = SERVICE_ID,
) : GameTransport {
    companion object {
        const val SERVICE_ID = "ir.dorehami.room"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    private val connected = LinkedHashSet<String>()
    private val reassemblers = HashMap<String, Frames.Reassembler>()
    private val ids = Frames.IdCounter()
    @Volatile private var closed = false

    private val _discovered = MutableStateFlow<List<NearbyRoom>>(emptyList())

    /** Rooms currently visible (guest side). */
    val discovered: StateFlow<List<NearbyRoom>> = _discovered

    /** The endpoint we are connected to as a guest (the host), once accepted. */
    @Volatile var hostEndpoint: String? = null
        private set

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val r = reassemblers.getOrPut(endpointId) { Frames.Reassembler { System.currentTimeMillis() } }
            r.feed(bytes)?.let { inbox.trySend(TransportEvent.Message(endpointId, String(it, Charsets.UTF_8))) }
            r.sweep()
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Same app, same service id, same room: accept. (Auth token could be shown for paranoia.)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connected += endpointId
                    if (hostEndpoint == null && !advertising) hostEndpoint = endpointId
                    inbox.trySend(TransportEvent.PeerConnected(endpointId))
                }
                else -> if (!advertising) inbox.trySend(TransportEvent.Closed)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected -= endpointId
            reassemblers.remove(endpointId)
            inbox.trySend(TransportEvent.PeerDisconnected(endpointId))
            if (endpointId == hostEndpoint) hostEndpoint = null
        }
    }

    @Volatile private var advertising = false

    // ---- host side ----

    /** Starts advertising this room. */
    suspend fun advertise(roomCode: String, hostName: String): Boolean {
        advertising = true
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        return await { client.startAdvertising("$roomCode|$hostName", serviceId, lifecycle, options) }
    }

    fun stopAdvertising() {
        advertising = false
        client.stopAdvertising()
    }

    // ---- guest side ----

    suspend fun discover(): Boolean {
        val callback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                if (info.serviceId != serviceId) return
                val parts = info.endpointName.split('|', limit = 2)
                val room = NearbyRoom(endpointId, parts.getOrElse(1) { info.endpointName }, parts[0])
                _discovered.value = _discovered.value.filter { it.endpointId != endpointId } + room
            }

            override fun onEndpointLost(endpointId: String) {
                _discovered.value = _discovered.value.filter { it.endpointId != endpointId }
            }
        }
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        return await { client.startDiscovery(serviceId, callback, options) }
    }

    fun stopDiscovery() = client.stopDiscovery()

    /** Requests a connection to a discovered host; completion arrives as [TransportEvent.PeerConnected]. */
    suspend fun connectTo(room: NearbyRoom, myName: String): Boolean =
        await { client.requestConnection(myName, room.endpointId, lifecycle) }

    // ---- GameTransport ----

    override suspend fun send(to: String?, text: String) {
        if (closed) return
        val targets = if (to != null) listOf(to) else connected.toList()
        if (targets.isEmpty()) return
        val chunks = Frames.encode(ids.next(), text.toByteArray(Charsets.UTF_8))
        for (chunk in chunks) client.sendPayload(targets, Payload.fromBytes(chunk))
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        inbox.trySend(TransportEvent.Closed)
    }

    private suspend fun await(block: () -> com.google.android.gms.tasks.Task<Void>): Boolean =
        suspendCancellableCoroutine { cont ->
            block()
                .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                .addOnFailureListener { if (cont.isActive) cont.resume(false) }
        }
}
