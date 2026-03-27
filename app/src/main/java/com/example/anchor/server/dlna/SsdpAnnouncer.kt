package com.example.anchor.server.dlna

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.server.service.AnchorServiceState
import com.example.anchor.server.service.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Broadcasts SSDP NOTIFY packets so UPnP control points on the LAN can
 * discover this Anchor server without sending an M-SEARCH first.
 *
 * Also responds to incoming M-SEARCH requests from control points.
 *
 * Changes from Phase 2:
 *  - Moved to server.dlna package.
 *  - Imports [AnchorServiceState] and [LogLevel] from server.service package.
 *  - Everything else is unchanged.
 */
class SsdpAnnouncer(
    private val context: Context,
    private val serverPort: Int = 8080
) {
    companion object {
        private const val TAG = "SsdpAnnouncer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val ANNOUNCE_INTERVAL = 30_000L
        private const val CACHE_MAX_AGE = 1800

        private val ADVERTISEMENT_TYPES = listOf(
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1",
            "urn:schemas-anchor:device:MediaServer:1"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val deviceUuid: String by lazy {
        val prefs = context.getSharedPreferences("anchor_device", Context.MODE_PRIVATE)
        prefs.getString("uuid", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("uuid", it).apply()
        }
    }

    private var announceJob: Job? = null
    private var listenerJob: Job? = null
    private var isRunning = false

    private val localIp: String?
        get() = NetworkUtils.getLocalIpAddress(context)

    // ── Lifecycle ─────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        isRunning = true
        AnchorServiceState.addLog("SSDP announcer starting")
        startMSearchListener()
        startPeriodicAnnouncements()
    }

    fun stop() {
        if (!isRunning) return
        scope.launch { sendByeByeNotifications() }
        announceJob?.cancel(); announceJob = null
        listenerJob?.cancel(); listenerJob = null
        isRunning = false
        AnchorServiceState.addLog("SSDP announcer stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── M-SEARCH listener ─────────────────────────────────────

    private fun startMSearchListener() {
        listenerJob = scope.launch {
            var socket: MulticastSocket? = null
            try {
                val group = InetAddress.getByName(SSDP_ADDRESS)
                socket = MulticastSocket(SSDP_PORT).apply {
                    reuseAddress = true
                    soTimeout = 0
                    joinAllInterfaces(group, this)
                }

                val buffer = ByteArray(8192)
                while (isActive && isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (message.startsWith("M-SEARCH", ignoreCase = true)) {
                            handleMSearch(message, packet.address, packet.port)
                        }
                    } catch (e: Exception) {
                        if (isActive && isRunning) Log.w(TAG, "M-SEARCH receive error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "M-SEARCH listener failed", e)
            } finally {
                socket?.close()
            }
        }
    }

    private suspend fun handleMSearch(
        message: String,
        senderAddress: InetAddress,
        senderPort: Int
    ) {
        val ip = localIp ?: return
        val st = Regex("ST:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.get(1)?.trim() ?: return

        val respond = st.equals("ssdp:all", ignoreCase = true)
                || st.equals("upnp:rootdevice", ignoreCase = true)
                || ADVERTISEMENT_TYPES.any { it.equals(st, ignoreCase = true) }

        if (!respond) return

        delay((100..500).random().toLong())

        val types = if (st.equals("ssdp:all", ignoreCase = true)) ADVERTISEMENT_TYPES
        else listOf(st)
        types.forEach { sendMSearchResponse(it, senderAddress, senderPort, ip) }
    }

    private fun sendMSearchResponse(
        st: String,
        target: InetAddress,
        port: Int,
        ip: String
    ) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            val usn = if (st == "upnp:rootdevice") "uuid:$deviceUuid::upnp:rootdevice"
            else "uuid:$deviceUuid::$st"
            val body = buildString {
                appendLine("HTTP/1.1 200 OK")
                appendLine("CACHE-CONTROL: max-age=$CACHE_MAX_AGE")
                appendLine(
                    "DATE: ${
                        SimpleDateFormat(
                            "EEE, dd MMM yyyy HH:mm:ss z",
                            Locale.US
                        ).format(Date())
                    }"
                )
                appendLine("EXT:")
                appendLine("LOCATION: http://$ip:$serverPort/dlna/device.xml")
                appendLine("SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.1 Anchor/1.0")
                appendLine("ST: $st")
                appendLine("USN: $usn")
                appendLine("BOOTID.UPNP.ORG: 1")
                appendLine("CONFIGID.UPNP.ORG: 1")
                appendLine()
            }
            val data = body.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, target, port))
        } catch (e: Exception) {
            Log.w(TAG, "M-SEARCH response failed", e)
        } finally {
            socket?.close()
        }
    }

    // ── Periodic NOTIFY ───────────────────────────────────────

    private fun startPeriodicAnnouncements() {
        announceJob = scope.launch {
            repeat(3) { sendAliveNotifications(); delay(1_000) }
            AnchorServiceState.addLog("SSDP: broadcasting on network", LogLevel.INFO)
            while (isActive && isRunning) {
                delay(ANNOUNCE_INTERVAL)
                sendAliveNotifications()
            }
        }
    }

    private suspend fun sendAliveNotifications() {
        val ip = localIp ?: return
        ADVERTISEMENT_TYPES.forEach { sendNotify(it, "ssdp:alive", ip); delay(50) }
    }

    private suspend fun sendByeByeNotifications() {
        val ip = localIp ?: return
        ADVERTISEMENT_TYPES.forEach { sendNotify(it, "ssdp:byebye", ip); delay(50) }
    }

    private fun sendNotify(nt: String, nts: String, ip: String) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
            val usn = if (nt == "upnp:rootdevice") "uuid:$deviceUuid::upnp:rootdevice"
            else "uuid:$deviceUuid::$nt"
            val body = buildString {
                appendLine("NOTIFY * HTTP/1.1")
                appendLine("HOST: $SSDP_ADDRESS:$SSDP_PORT")
                appendLine("CACHE-CONTROL: max-age=$CACHE_MAX_AGE")
                appendLine("LOCATION: http://$ip:$serverPort/dlna/device.xml")
                appendLine("NT: $nt")
                appendLine("NTS: $nts")
                appendLine("SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.1 Anchor/1.0")
                appendLine("USN: $usn")
                appendLine("BOOTID.UPNP.ORG: 1")
                appendLine("CONFIGID.UPNP.ORG: 1")
                appendLine()
            }
            val data = body.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            socket.send(DatagramPacket(data, data.size, address, SSDP_PORT))
        } catch (e: Exception) {
            Log.w(TAG, "NOTIFY send failed", e)
        } finally {
            socket?.close()
        }
    }

    // ── Helper ────────────────────────────────────────────────

    private fun joinAllInterfaces(group: InetAddress, socket: MulticastSocket) {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                    runCatching { socket.joinGroup(InetSocketAddress(group, SSDP_PORT), ni) }
                }
            }
        } catch (e: Exception) {
            runCatching { socket.joinGroup(InetSocketAddress(group, SSDP_PORT), null) }
        }
    }
}