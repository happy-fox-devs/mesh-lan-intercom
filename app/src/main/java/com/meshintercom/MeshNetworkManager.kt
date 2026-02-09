package com.meshintercom

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.nio.charset.StandardCharsets

class MeshNetworkManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_CLUSTER

    // private val serviceId = "com.meshintercom.SERVICE_ID" // Removed hardcoded ID
    private var currentServiceId = ""

    // Unique ID for this device in the mesh (Random Int)
    private val myMeshId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    private var myNickname = "User_$myMeshId"

    // Sequence number for my packets
    private var packetSequence = 0

    // Connected endpoints: Map<EndpointId, Nickname>
    private val connectedEndpoints = mutableMapOf<String, String>()

    // Deduplication Cache: Map<PacketHash, Timestamp>
    // We store a hash of (SenderID + PacketID) to identify uniqueness
    private val seenPackets = object : java.util.LinkedHashMap<Long, Long>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?): Boolean {
            return size > 1000 // Keep last 1000 packets
        }
    }

    // Callback for when audio data is received from the network
    var onAudioPacketReceived: ((ByteArray) -> Unit)? = null

    // Callback for UI updates (e.g., peer list)
    var onPeersChanged: ((List<String>) -> Unit)? = null

    fun start(secretKey: String, nickname: String) {
        if (secretKey.isBlank()) {
            Log.e(TAG, "Cannot start with empty secret key")
            return
        }
        if (nickname.isNotBlank()) {
            myNickname = nickname
        }

        // Generate ServiceID based on Secret Key
        // Service ID must be unique but common for the group.
        // Format: com.meshintercom.chan.{HASH}
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(secretKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8) // Take first 8 chars of hash for brevity

        currentServiceId = "com.meshintercom.chan.$hash"
        Log.d(
            TAG,
            "Starting Mesh with Service ID: $currentServiceId (Key: $secretKey, Name: $myNickname)"
        )

        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        seenPackets.clear()
        onPeersChanged?.invoke(emptyList())
    }

    fun sendAudioPacket(audioData: ByteArray) {
        if (connectedEndpoints.isEmpty()) return

        // 1. Create Mesh Packet
        // Header: [TTL(1)][SenderID(4)][Sequence(4)]
        val ttl: Byte = 3 // Max 3 hops
        val seq = packetSequence++

        val packet = java.nio.ByteBuffer.allocate(9 + audioData.size)
            .put(ttl)
            .putInt(myMeshId)
            .putInt(seq)
            .put(audioData)
            .array()

        // 2. Mark as seen by myself
        val packetHash = ((myMeshId.toLong() shl 32) or (seq.toLong() and 0xFFFFFFFFL))
        seenPackets[packetHash] = System.currentTimeMillis()

        // 3. Broadcast
        val payload = Payload.fromBytes(packet)
        connectionsClient.sendPayload(connectedEndpoints.keys.toList(), payload)
            .addOnFailureListener { e -> Log.e(TAG, "Error sending logic packet", e) }
    }

    // ... (Advertising/Discovery methods same as before) ...

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val data = payload.asBytes() ?: return
                if (data.size < 9) return // Invalid packet

                val buffer = java.nio.ByteBuffer.wrap(data)
                var ttl = buffer.get()
                val senderId = buffer.int
                val seq = buffer.int

                // 1. Deduplication Check
                val packetHash = ((senderId.toLong() shl 32) or (seq.toLong() and 0xFFFFFFFFL))
                if (seenPackets.containsKey(packetHash)) {
                    return // Already processed
                }
                seenPackets[packetHash] = System.currentTimeMillis()

                // 2. Play Audio (if it's not from me, theoretically)
                // Extract audio data (offset 9)
                val audioSize = data.size - 9
                val audioData = ByteArray(audioSize)
                System.arraycopy(data, 9, audioData, 0, audioSize)

                if (senderId != myMeshId) {
                    onAudioPacketReceived?.invoke(audioData)
                }

                // 3. Relay Logic (Flood)
                if (ttl > 0) {
                    ttl = (ttl - 1).toByte()
                    // Re-pack with new TTL
                    buffer.position(0)
                    buffer.put(ttl) // Update TTL in place

                    // Send to everyone EXCEPT the one who sent it to us
                    val targets = connectedEndpoints.keys.filter { it != endpointId }
                    if (targets.isNotEmpty()) {
                        val relayPayload =
                            Payload.fromBytes(data) // 'data' now has new TTL in buffer backing array?
                        // ByteBuffer updates the array backing it.
                        connectionsClient.sendPayload(targets, relayPayload)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun startAdvertising() {
        if (currentServiceId.isEmpty()) return
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startAdvertising(
            myNickname, currentServiceId, connectionLifecycleCallback, advertisingOptions
        )
            .addOnSuccessListener { Log.d(TAG, "Started Advertising") }
            .addOnFailureListener { e -> Log.e(TAG, "Error starting Advertising", e) }
    }

    private fun startDiscovery() {
        if (currentServiceId.isEmpty()) return
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            currentServiceId, endpointDiscoveryCallback, discoveryOptions
        )
            .addOnSuccessListener { Log.d(TAG, "Started Discovery") }
            .addOnFailureListener { e -> Log.e(TAG, "Error starting Discovery", e) }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            // In P2P_CLUSTER, anyone can request connection to anyone.
            // We automatically request connection.
            connectionsClient.requestConnection(myNickname, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e -> Log.e(TAG, "Error requesting connection", e) }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated: $endpointId (${info.endpointName})")
            // Automatically accept connection
            connectionsClient.acceptConnection(endpointId, payloadCallback)

            // Store the endpoint name temporarily (will confirm on Result)
            connectedEndpoints[endpointId] = info.endpointName
            onPeersChanged?.invoke(connectedEndpoints.values.toList())
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to: $endpointId")
                    // Name already stored in onConnectionInitiated
                    // Just notify again to be sure? Not needed if flow is correct.
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected: $endpointId")
                    connectedEndpoints.remove(endpointId)
                    onPeersChanged?.invoke(connectedEndpoints.values.toList())
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error: $endpointId")
                    connectedEndpoints.remove(endpointId)
                    onPeersChanged?.invoke(connectedEndpoints.values.toList())
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected: $endpointId")
            connectedEndpoints.remove(endpointId)
            onPeersChanged?.invoke(connectedEndpoints.values.toList())
        }
    }


    companion object {
        private const val TAG = "MeshNetworkManager"
    }
}
