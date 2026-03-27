package com.example.anchor.data.source.remote

import android.util.Log
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.dto.SsdpMessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Handles all raw SSDP UDP socket operations:
 *  - Listening for multicast NOTIFY packets (device alive / byebye)
 *  - Sending M-SEARCH requests and collecting unicast responses
 *
 * Produces [SsdpMessageDto] values; no domain types cross this boundary.
 * All socket work is on [Dispatchers.IO].
 */
class SsdpDataSource {

    companion object {
        private const val TAG = "SsdpDataSource"
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        private const val BUFFER_SIZE = 8_192
        private const val SOCKET_TIMEOUT = 3_000          // ms — receive timeout
        private const val SEARCH_TIMEOUT = 3_000L         // ms — wait for M-SEARCH replies
        private const val RESPONSE_WINDOW = 3_000L         // ms — window to collect responses
    }

    // ── Multicast listener ────────────────────────────────────

    /**
     * Returns a cold [Flow] that emits every incoming SSDP [SsdpMessageDto]
     * received on the multicast group.
     *
     * The socket is opened when collection starts and closed when the flow
     * is cancelled (collector leaves the scope).
     *
     * Designed to be collected inside a [kotlinx.coroutines.CoroutineScope]
     * managed by the caller — typically [UpnpDiscoveryManager].
     */
    fun multicastMessages(): Flow<Pair<SsdpMessageDto, String>> = callbackFlow {
        var socket: MulticastSocket? = null
        try {
            val group = InetAddress.getByName(SSDP_ADDRESS)
            socket = MulticastSocket(SSDP_PORT).apply {
                reuseAddress = true
                soTimeout = SOCKET_TIMEOUT
                joinAllInterfaces(group, this)
            }

            Log.d(TAG, "Multicast listener started on $SSDP_ADDRESS:$SSDP_PORT")

            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val msg = SsdpMessageDto.parse(packet.data.copyOf(packet.length))
                    if (msg != null) {
                        val srcIp = packet.address?.hostAddress ?: ""
                        trySend(msg to srcIp)
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal — keep listening
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Multicast receive error", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multicast listener failed", e)
            close(e)
        } finally {
            socket?.close()
            Log.d(TAG, "Multicast listener closed")
        }
        // callbackFlow must call awaitClose when the producer is event-driven;
        // here the while-loop drives it, so close() above is sufficient.
    }

    // ── M-SEARCH ──────────────────────────────────────────────

    /**
     * Sends an M-SEARCH request for [searchTarget] and collects unicast
     * responses for [timeoutMs] milliseconds.
     *
     * @return All valid [SsdpMessageDto] responses received within the window.
     */
    suspend fun search(
        searchTarget: String = "ssdp:all",
        timeoutMs: Long = SEARCH_TIMEOUT
    ): List<Pair<SsdpMessageDto, String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<SsdpMessageDto, String>>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = SOCKET_TIMEOUT
            }

            val message = SsdpMessageDto.buildMSearch(searchTarget)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            socket.send(DatagramPacket(message, message.size, address, SSDP_PORT))
            Log.d(TAG, "M-SEARCH sent for: $searchTarget")

            val buffer = ByteArray(BUFFER_SIZE)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val msg = SsdpMessageDto.parse(buffer.copyOf(packet.length))
                    if (msg != null && msg.type == SsdpMessageType.RESPONSE) {
                        val srcIp = packet.address?.hostAddress ?: ""
                        results.add(msg to srcIp)
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep collecting until deadline
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "M-SEARCH failed for $searchTarget", e)
        } finally {
            socket?.close()
        }
        results
    }

    // ── SSDP announcer ────────────────────────────────────────

    /**
     * Sends a single NOTIFY message (alive or byebye) to the SSDP multicast group.
     * Fire-and-forget — errors are logged but not propagated.
     */
    suspend fun sendNotify(message: String) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
            val data = message.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            socket.send(DatagramPacket(data, data.size, address, SSDP_PORT))
        } catch (e: Exception) {
            Log.w(TAG, "NOTIFY send failed", e)
        } finally {
            socket?.close()
        }
    }

    /**
     * Sends a unicast M-SEARCH response directly to [targetAddress]:[targetPort].
     */
    suspend fun sendResponse(
        message: String,
        targetAddress: InetAddress,
        targetPort: Int
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            val data = message.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, targetAddress, targetPort))
        } catch (e: Exception) {
            Log.w(TAG, "Response send failed", e)
        } finally {
            socket?.close()
        }
    }

    // ── Internal helpers ──────────────────────────────────────

    /**
     * Attempts to join the multicast [group] on every eligible network
     * interface.  Falls back to interface-less join on error.
     */
    private fun joinAllInterfaces(group: InetAddress, socket: MulticastSocket) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var joined = false
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                    runCatching {
                        socket.joinGroup(InetSocketAddress(group, SSDP_PORT), ni)
                        joined = true
                        Log.d(TAG, "Joined multicast on ${ni.name}")
                    }
                }
            }
            if (!joined) {
                socket.joinGroup(InetSocketAddress(group, SSDP_PORT), null)
            }
        } catch (e: Exception) {
            runCatching { socket.joinGroup(InetSocketAddress(group, SSDP_PORT), null) }
        }
    }
}