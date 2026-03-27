package com.example.anchor.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.anchor.core.util.NetworkUtils
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
 * Handles SSDP (Simple Service Discovery Protocol) announcements.
 */
class SsdpAnnouncer(
    private val context: Context,
    private val serverPort: Int = 8080
) {
    companion object {
        private const val TAG = "SsdpAnnouncer"

        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val ANNOUNCE_INTERVAL_MS = 30000L
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

    // Unique device identifier - public property with custom getter name
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

    fun start() {
        if (isRunning) {
            Log.d(TAG, "Announcer already running")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting SSDP announcer")
        AnchorServiceState.addLog("SSDP announcer starting")

        startMSearchListener()
        startPeriodicAnnouncements()
    }

    fun stop() {
        if (!isRunning) return

        Log.d(TAG, "Stopping SSDP announcer")

        scope.launch {
            sendByeByeNotifications()
        }

        announceJob?.cancel()
        listenerJob?.cancel()
        announceJob = null
        listenerJob = null
        isRunning = false

        AnchorServiceState.addLog("SSDP announcer stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private fun startMSearchListener() {
        listenerJob = scope.launch {
            var socket: MulticastSocket? = null

            try {
                val group = InetAddress.getByName(SSDP_ADDRESS)
                socket = MulticastSocket(SSDP_PORT).apply {
                    reuseAddress = true
                    soTimeout = 0

                    try {
                        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                        while (networkInterfaces.hasMoreElements()) {
                            val ni = networkInterfaces.nextElement()
                            if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                                try {
                                    joinGroup(InetSocketAddress(group, SSDP_PORT), ni)
                                } catch (e: Exception) {
                                    // Some interfaces may not support joining
                                }
                            }
                        }
                    } catch (e: Exception) {
                        joinGroup(InetSocketAddress(group, SSDP_PORT), null)
                    }
                }

                Log.d(TAG, "M-SEARCH listener started")

                val buffer = ByteArray(8192)

                while (isActive && isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)

                        if (message.startsWith("M-SEARCH", ignoreCase = true)) {
                            handleMSearchRequest(
                                message = message,
                                senderAddress = packet.address,
                                senderPort = packet.port
                            )
                        }
                    } catch (e: Exception) {
                        if (isActive && isRunning) {
                            Log.w(TAG, "Error receiving M-SEARCH", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "M-SEARCH listener failed", e)
            } finally {
                socket?.close()
            }
        }
    }

    private suspend fun handleMSearchRequest(
        message: String,
        senderAddress: InetAddress,
        senderPort: Int
    ) {
        val ip = localIp ?: return

        val stMatch = Regex("ST:\\s*(.+)", RegexOption.IGNORE_CASE).find(message)
        val searchTarget = stMatch?.groupValues?.get(1)?.trim() ?: return

        Log.d(TAG, "M-SEARCH from ${senderAddress.hostAddress}:$senderPort for $searchTarget")

        val shouldRespond = when {
            searchTarget.equals("ssdp:all", ignoreCase = true) -> true
            searchTarget.equals("upnp:rootdevice", ignoreCase = true) -> true
            ADVERTISEMENT_TYPES.any { it.equals(searchTarget, ignoreCase = true) } -> true
            else -> false
        }

        if (!shouldRespond) return

        delay((100..500).random().toLong())

        val responseTypes = if (searchTarget.equals("ssdp:all", ignoreCase = true)) {
            ADVERTISEMENT_TYPES
        } else {
            listOf(searchTarget)
        }

        responseTypes.forEach { type ->
            sendMSearchResponse(type, senderAddress, senderPort, ip)
        }
    }

    private fun sendMSearchResponse(
        searchTarget: String,
        targetAddress: InetAddress,
        targetPort: Int,
        localIp: String
    ) {
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket()

            val response = buildMSearchResponse(searchTarget, localIp)
            val data = response.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(data, data.size, targetAddress, targetPort)

            socket.send(packet)
            Log.d(
                TAG,
                "Sent M-SEARCH response for $searchTarget to ${targetAddress.hostAddress}:$targetPort"
            )

        } catch (e: Exception) {
            Log.w(TAG, "Failed to send M-SEARCH response", e)
        } finally {
            socket?.close()
        }
    }

    private fun buildMSearchResponse(searchTarget: String, localIp: String): String {
        val usn = if (searchTarget == "upnp:rootdevice") {
            "uuid:$deviceUuid::upnp:rootdevice"
        } else {
            "uuid:$deviceUuid::$searchTarget"
        }

        return buildString {
            appendLine("HTTP/1.1 200 OK")
            appendLine("CACHE-CONTROL: max-age=$CACHE_MAX_AGE")
            appendLine(
                "DATE: ${
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(
                        Date()
                    )
                }"
            )
            appendLine("EXT:")
            appendLine("LOCATION: http://$localIp:$serverPort/dlna/device.xml")
            appendLine("SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.1 Anchor/1.0")
            appendLine("ST: $searchTarget")
            appendLine("USN: $usn")
            appendLine("BOOTID.UPNP.ORG: 1")
            appendLine("CONFIGID.UPNP.ORG: 1")
            appendLine()
        }
    }

    private fun startPeriodicAnnouncements() {
        announceJob = scope.launch {
            repeat(3) {
                sendAliveNotifications()
                delay(1000)
            }

            AnchorServiceState.addLog("SSDP: Broadcasting on network", LogLevel.INFO)

            while (isActive && isRunning) {
                delay(ANNOUNCE_INTERVAL_MS)
                sendAliveNotifications()
            }
        }
    }

    private suspend fun sendAliveNotifications() {
        val ip = localIp ?: return

        ADVERTISEMENT_TYPES.forEach { type ->
            sendNotify(type, "ssdp:alive", ip)
            delay(50)
        }
    }

    private suspend fun sendByeByeNotifications() {
        val ip = localIp ?: return

        ADVERTISEMENT_TYPES.forEach { type ->
            sendNotify(type, "ssdp:byebye", ip)
            delay(50)
        }
    }

    private fun sendNotify(notificationType: String, notificationSubType: String, localIp: String) {
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                broadcast = true
            }

            val message = buildNotifyMessage(notificationType, notificationSubType, localIp)
            val data = message.toByteArray(Charsets.UTF_8)
            val address = InetAddress.getByName(SSDP_ADDRESS)
            val packet = DatagramPacket(data, data.size, address, SSDP_PORT)

            socket.send(packet)

        } catch (e: Exception) {
            Log.w(TAG, "Failed to send NOTIFY", e)
        } finally {
            socket?.close()
        }
    }

    private fun buildNotifyMessage(
        notificationType: String,
        notificationSubType: String,
        localIp: String
    ): String {
        val usn = if (notificationType == "upnp:rootdevice") {
            "uuid:$deviceUuid::upnp:rootdevice"
        } else {
            "uuid:$deviceUuid::$notificationType"
        }

        return buildString {
            appendLine("NOTIFY * HTTP/1.1")
            appendLine("HOST: $SSDP_ADDRESS:$SSDP_PORT")
            appendLine("CACHE-CONTROL: max-age=$CACHE_MAX_AGE")
            appendLine("LOCATION: http://$localIp:$serverPort/dlna/device.xml")
            appendLine("NT: $notificationType")
            appendLine("NTS: $notificationSubType")
            appendLine("SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.1 Anchor/1.0")
            appendLine("USN: $usn")
            appendLine("BOOTID.UPNP.ORG: 1")
            appendLine("CONFIGID.UPNP.ORG: 1")
            appendLine()
        }
    }
}