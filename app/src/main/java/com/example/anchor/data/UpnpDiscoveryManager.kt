// app/src/main/java/com/example/anchor/discovery/UpnpDiscoveryManager.kt

package com.example.anchor.data

import android.content.Context
import android.util.Log
import com.example.anchor.core.util.MulticastLockManager
import com.example.anchor.data.model.DiscoveredDevice
import com.example.anchor.data.model.ServerType
import com.example.anchor.data.model.SsdpMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Manages UPnP/SSDP device discovery on the local network.
 *
 * Uses multicast UDP to:
 * 1. Listen for NOTIFY announcements from devices
 * 2. Send M-SEARCH requests and process responses
 */
class UpnpDiscoveryManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "UpnpDiscoveryManager"

        // SSDP Multicast address and port
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        // Discovery settings
        private const val RECEIVE_BUFFER_SIZE = 8192
        private const val SOCKET_TIMEOUT_MS = 3000
        private const val SEARCH_INTERVAL_MS = 30000L  // 30 seconds between searches
        private const val STALE_DEVICE_THRESHOLD_MS = 5 * 60 * 1000L  // 5 minutes
    }

    // Coroutine scope for discovery operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Multicast lock manager
    private val multicastLockManager = MulticastLockManager(context)

    // Discovery state
    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, DiscoveredDevice>> =
        _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Active jobs
    private var listenerJob: Job? = null
    private var searchJob: Job? = null
    private var cleanupJob: Job? = null

    /**
     * Starts continuous device discovery.
     * Listens for NOTIFY messages and periodically sends M-SEARCH requests.
     */
    fun startDiscovery() {
        if (_isScanning.value) {
            Log.d(TAG, "Discovery already running")
            return
        }

        Log.d(TAG, "Starting UPnP discovery")
        _isScanning.value = true
        _lastError.value = null

        // Acquire multicast lock
        multicastLockManager.acquire()

        // Start listening for multicast messages
        startMulticastListener()

        // Start periodic M-SEARCH
        startPeriodicSearch()

        // Start cleanup of stale devices
        startStaleDeviceCleanup()
    }

    /**
     * Stops device discovery and releases resources.
     */
    fun stopDiscovery() {
        Log.d(TAG, "Stopping UPnP discovery")

        listenerJob?.cancel()
        searchJob?.cancel()
        cleanupJob?.cancel()

        listenerJob = null
        searchJob = null
        cleanupJob = null

        multicastLockManager.release()
        _isScanning.value = false
    }

    /**
     * Performs a single M-SEARCH scan and waits for responses.
     */
    suspend fun performSingleScan(): List<DiscoveredDevice> {
        return withContext(Dispatchers.IO) {
            try {
                multicastLockManager.acquire()

                val devices = mutableListOf<DiscoveredDevice>()

                // Send M-SEARCH for all devices
                sendMSearch("ssdp:all")

                // Also search specifically for media servers
                sendMSearch("urn:schemas-upnp-org:device:MediaServer:1")

                // Wait and collect responses
                delay(3000)

                devices.addAll(_discoveredDevices.value.values)
                devices
            } catch (e: Exception) {
                Log.e(TAG, "Single scan failed", e)
                _lastError.value = e.message
                emptyList()
            } finally {
                multicastLockManager.release()
            }
        }
    }

    /**
     * Clears all discovered devices.
     */
    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
    }

    /**
     * Starts the multicast listener for NOTIFY messages.
     */
    private fun startMulticastListener() {
        listenerJob = scope.launch {
            var socket: MulticastSocket? = null

            try {
                val group = InetAddress.getByName(SSDP_ADDRESS)
                socket = MulticastSocket(SSDP_PORT).apply {
                    reuseAddress = true
                    soTimeout = SOCKET_TIMEOUT_MS

                    // Join multicast group on all available interfaces
                    try {
                        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                        while (networkInterfaces.hasMoreElements()) {
                            val ni = networkInterfaces.nextElement()
                            if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                                try {
                                    joinGroup(InetSocketAddress(group, SSDP_PORT), ni)
                                    Log.d(TAG, "Joined multicast on interface: ${ni.name}")
                                } catch (e: Exception) {
                                    // Some interfaces may not support joining
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Fallback: join without specifying interface
                        joinGroup(InetSocketAddress(group, SSDP_PORT), null)
                    }
                }

                Log.d(TAG, "Multicast listener started on $SSDP_ADDRESS:$SSDP_PORT")

                val buffer = ByteArray(RECEIVE_BUFFER_SIZE)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val message = SsdpMessage.parse(packet.data.copyOf(packet.length))
                        if (message != null) {
                            processMessage(message, packet.address.hostAddress ?: "")
                        }
                    } catch (e: SocketTimeoutException) {
                        // Normal timeout, continue listening
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Error receiving packet", e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Multicast listener failed", e)
                _lastError.value = "Discovery failed: ${e.message}"
            } finally {
                socket?.close()
                Log.d(TAG, "Multicast listener stopped")
            }
        }
    }

    /**
     * Starts periodic M-SEARCH broadcasts.
     */
    private fun startPeriodicSearch() {
        searchJob = scope.launch {
            // Initial search immediately
            sendMSearch("ssdp:all")
            sendMSearch("urn:schemas-upnp-org:device:MediaServer:1")

            // Then periodic searches
            while (isActive) {
                delay(SEARCH_INTERVAL_MS)

                sendMSearch("ssdp:all")
                delay(500)  // Small delay between different search types
                sendMSearch("urn:schemas-upnp-org:device:MediaServer:1")
            }
        }
    }

    /**
     * Starts periodic cleanup of stale devices.
     */
    private fun startStaleDeviceCleanup() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60000)  // Check every minute

                val now = System.currentTimeMillis()
                val currentDevices = _discoveredDevices.value.toMutableMap()
                val staleUsns = currentDevices.filter { (_, device) ->
                    now - device.lastSeen > STALE_DEVICE_THRESHOLD_MS
                }.keys

                if (staleUsns.isNotEmpty()) {
                    staleUsns.forEach { currentDevices.remove(it) }
                    _discoveredDevices.value = currentDevices
                    Log.d(TAG, "Removed ${staleUsns.size} stale devices")
                }
            }
        }
    }

    /**
     * Sends an M-SEARCH broadcast to discover devices.
     */
    private suspend fun sendMSearch(searchTarget: String) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = SOCKET_TIMEOUT_MS
                }

                val message = SsdpMessage.createMSearchMessage(searchTarget)
                val address = InetAddress.getByName(SSDP_ADDRESS)
                val packet = DatagramPacket(message, message.size, address, SSDP_PORT)

                socket.send(packet)
                Log.d(TAG, "Sent M-SEARCH for: $searchTarget")

                // Listen for responses
                val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
                val endTime = System.currentTimeMillis() + 3000  // 3 second timeout

                while (System.currentTimeMillis() < endTime) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(responsePacket)

                        val response = SsdpMessage.parse(buffer.copyOf(responsePacket.length))
                        if (response != null && response.type == SsdpMessage.MessageType.RESPONSE) {
                            processMessage(response, responsePacket.address.hostAddress ?: "")
                        }
                    } catch (e: SocketTimeoutException) {
                        // Continue until endTime
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "M-SEARCH failed for $searchTarget", e)
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * Processes a received SSDP message.
     */
    private suspend fun processMessage(message: SsdpMessage, sourceIp: String) {
        // Handle byebye (device leaving)
        if (message.isByeBye) {
            message.usn?.let { usn ->
                val currentDevices = _discoveredDevices.value.toMutableMap()
                currentDevices.remove(usn)
                _discoveredDevices.value = currentDevices
                Log.d(TAG, "Device left: $usn")
            }
            return
        }

        // Process alive/response messages
        val location = message.location ?: return
        val usn = message.usn ?: return
        val serverType = message.getServerType()

        // Skip unknown device types unless they have a valid location
        if (serverType == ServerType.UNKNOWN && !location.startsWith("http")) {
            return
        }

        // Extract IP and port from location
        val (ip, port) = extractIpAndPort(location)

        // Create or update device entry
        val device = DiscoveredDevice(
            usn = usn,
            location = location,
            serverType = serverType,
            ipAddress = ip ?: sourceIp,
            port = port ?: 80,
            lastSeen = System.currentTimeMillis()
        )

        // Fetch additional details asynchronously
        scope.launch {
            val enrichedDevice = fetchDeviceDetails(device)

            val currentDevices = _discoveredDevices.value.toMutableMap()
            currentDevices[usn] = enrichedDevice
            _discoveredDevices.value = currentDevices

            Log.d(TAG, "Discovered: ${enrichedDevice.displayName} (${enrichedDevice.serverType})")
        }
    }

    /**
     * Extracts IP address and port from a URL location.
     */
    private fun extractIpAndPort(location: String): Pair<String?, Int?> {
        return try {
            val url = URL(location)
            val port = if (url.port == -1) url.defaultPort else url.port
            Pair(url.host, port)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Fetches device description XML to get friendly name and other details.
     */
    private suspend fun fetchDeviceDetails(device: DiscoveredDevice): DiscoveredDevice {
        return withContext(Dispatchers.IO) {
            try {
                val xml = withTimeoutOrNull(5000) {
                    URL(device.location).readText()
                }

                if (xml != null) {
                    val description = DeviceDescriptionParser.parse(xml)
                    if (description != null) {
                        return@withContext device.copy(
                            friendlyName = description.friendlyName.ifEmpty { device.friendlyName },
                            manufacturer = description.manufacturer,
                            modelName = description.modelName
                        )
                    }
                }

                device
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch device details: ${device.location}", e)
                device
            }
        }
    }

    /**
     * Releases all resources.
     */
    fun destroy() {
        stopDiscovery()
        scope.cancel()
    }
}