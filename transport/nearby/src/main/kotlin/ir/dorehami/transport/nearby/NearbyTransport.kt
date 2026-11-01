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

        /** Hard cap for envelopes queued before a peer's link is confirmed (~3 minutes of chat). */
        private const val MAX_PENDING_BYTES = 256 * 1024
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = inbox.receiveAsFlow()

    private val connected = LinkedHashSet<String>()
    private val reassemblers = HashMap<String, Frames.Reassembler>()
    private val ids = Frames.IdCounter()
    @Volatile private var closed = false

    /**
     * Envelopes addressed to a peer that GMS has not confirmed yet.
     *
     * A guest's `lobby.join` is sent the moment `requestConnection` resolves, which is *before*
     * `onConnectionResult(STATUS_OK)`. Dropping that first envelope used to leave the lobby stuck on
     * "waiting for the host" until the 20 s timeout — the main reason Bluetooth felt unstable.
     */
    private val pending = LinkedHashMap<String, MutableList<ByteArray>>()
    private val pendingBytes = HashMap<String, Int>()
    private var advertisedName: String? = null

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

    @Volatile private var advertising = false

    private val advertisingOptions: AdvertisingOptions =
        AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

    private val lifecycle: ConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Same app, same service id, same room: accept. (Auth token could be shown for paranoia.)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    synchronized(connected) { connected += endpointId }
                    if (hostEndpoint == null && !advertising) hostEndpoint = endpointId
                    flushPending(endpointId)
                    inbox.trySend(TransportEvent.PeerConnected(endpointId))
                }
                else -> if (!advertising) inbox.trySend(TransportEvent.Closed)
            }
        }

        override fun onDisconnected(endpointId: String) {
            synchronized(connected) { connected -= endpointId }
            reassemblers.remove(endpointId)
            synchronized(pending) {
                pending.remove(endpointId)
                pendingBytes.remove(endpointId)
            }
            inbox.trySend(TransportEvent.PeerDisconnected(endpointId))
            if (endpointId == hostEndpoint) hostEndpoint = null
            // A host keeps its room open after someone walks away: make sure the room is still
            // discoverable, otherwise the remaining players can never refill the table.
            val name = advertisedName
            if (advertising && name != null && !closed) {
                runCatching {
                    client.startAdvertising(name, serviceId, lifecycle, advertisingOptions)
                }
            }
        }
    }

    // ---- host side ----

    /** Starts advertising this room. */
    suspend fun advertise(roomCode: String, hostName: String): Boolean {
        val name = "$roomCode|$hostName"
        advertising = true
        advertisedName = name
        return await { client.startAdvertising(name, serviceId, lifecycle, advertisingOptions) }
    }

    fun stopAdvertising() {
        advertising = false
        advertisedName = null
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
        val body = text.toByteArray(Charsets.UTF_8)
        if (to != null && !isConnected(to)) {
            synchronized(pending) {
                val used = pendingBytes[to] ?: 0
                if (used + body.size > MAX_PENDING_BYTES) return
                pending.getOrPut(to) { mutableListOf() }.add(body)
                pendingBytes[to] = used + body.size
            }
            return
        }
        val chunks = synchronized(ids) { Frames.encode(ids.next(), body) }
        val targets = if (to != null) listOf(to) else peers()
        if (targets.isEmpty()) return
        for (chunk in chunks) client.sendPayload(targets, Payload.fromBytes(chunk))
    }

    private fun isConnected(peer: String): Boolean = synchronized(connected) { peer in connected }

    private fun peers(): List<String> = synchronized(connected) { connected.toList() }

    /** Sends everything that was queued for [endpointId] while the link was still being confirmed. */
    private fun flushPending(endpointId: String) {
        val queued: List<ByteArray> = synchronized(pending) {
            pendingBytes.remove(endpointId)
            pending.remove(endpointId)
        } ?: return
        for (body in queued) {
            val chunks = synchronized(ids) { Frames.encode(ids.next(), body) }
            for (chunk in chunks) {
                runCatching { client.sendPayload(listOf(endpointId), Payload.fromBytes(chunk)) }
            }
        }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        advertising = false
        advertisedName = null
        synchronized(pending) {
            pending.clear()
            pendingBytes.clear()
        }
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
