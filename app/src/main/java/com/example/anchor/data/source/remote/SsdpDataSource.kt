package com.example.anchor.data.source.remote

import android.util.Log
import com.example.anchor.core.config.AnchorConfig
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.dto.SsdpMessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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
        private const val BUFFER_SIZE = 8_192
        private const val SEARCH_TIMEOUT = 3_000L         // ms — wait for M-SEARCH replies
    }

    // ── Multicast listener ────────────────────────────────────

    /**
     * Returns a cold [Flow] that emits every incoming SSDP [SsdpMessageDto]
     * received on the multicast group.
     *
     * The socket is opened when collection starts and closed when the flow
     * is cancelled (collector leaves the scope).
     */
    fun multicastMessages(): Flow<Pair<SsdpMessageDto, String>> = callbackFlow {
        var socket: MulticastSocket? = null
        try {
            socket = MulticastSocket(AnchorConfig.Discovery.SSDP_PORT).apply {
                reuseAddress = true
                soTimeout = AnchorConfig.Discovery.SOCKET_TIMEOUT_MS
                val group = InetAddress.getByName(AnchorConfig.Discovery.SSDP_ADDRESS)
                joinAllInterfaces(group, this)
            }

            Log.d(TAG, "Multicast listener started on ${AnchorConfig.Discovery.SSDP_ADDRESS}:${AnchorConfig.Discovery.SSDP_PORT}")

            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    SsdpMessageDto.parse(packet.data.copyOf(packet.length))
                        .onSuccess { msg ->
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
            throw e // Let callbackFlow handle it
        } finally {
            socket?.close()
            Log.d(TAG, "Multicast listener closed")
        }
        
        awaitClose {
            socket?.close()
        }
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
                soTimeout = AnchorConfig.Discovery.SOCKET_TIMEOUT_MS
            }

            val message = SsdpMessageDto.buildMSearch(searchTarget)
            val address = InetAddress.getByName(AnchorConfig.Discovery.SSDP_ADDRESS)
            socket.send(DatagramPacket(message, message.size, address, AnchorConfig.Discovery.SSDP_PORT))
            Log.d(TAG, "M-SEARCH sent for: $searchTarget")

            val buffer = ByteArray(BUFFER_SIZE)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    SsdpMessageDto.parse(buffer.copyOf(packet.length))
                        .onSuccess { msg ->
                            if (msg.type == SsdpMessageType.RESPONSE) {
                                val srcIp = packet.address?.hostAddress ?: ""
                                results.add(msg to srcIp)
                            }
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
            val address = InetAddress.getByName(AnchorConfig.Discovery.SSDP_ADDRESS)
            socket.send(DatagramPacket(data, data.size, address, AnchorConfig.Discovery.SSDP_PORT))
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
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback && networkInterface.supportsMulticast()) {
                    runCatching {
                        socket.joinGroup(InetSocketAddress(group, AnchorConfig.Discovery.SSDP_PORT), networkInterface)
                        joined = true
                        Log.d(TAG, "Joined multicast on ${networkInterface.name}")
                    }
                }
            }
            if (!joined) {
                socket.joinGroup(InetSocketAddress(group, AnchorConfig.Discovery.SSDP_PORT), null)
            }
        } catch (e: Exception) {
            runCatching { socket.joinGroup(InetSocketAddress(group, AnchorConfig.Discovery.SSDP_PORT), null) }
        }
    }
}
