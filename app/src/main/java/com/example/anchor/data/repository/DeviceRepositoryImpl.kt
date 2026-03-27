package com.example.anchor.data.repository

import android.util.Log
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.dto.SsdpMessageType
import com.example.anchor.data.mapper.DeviceMapper.enrichWithDescription
import com.example.anchor.data.mapper.DeviceMapper.refreshed
import com.example.anchor.data.mapper.DeviceMapper.toDeviceStub
import com.example.anchor.data.source.remote.HttpClientDataSource
import com.example.anchor.data.source.remote.SsdpDataSource
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import com.example.anchor.domain.repository.DeviceRepository
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

/**
 * Concrete implementation of [DeviceRepository].
 *
 * Orchestrates [SsdpDataSource] (UDP) and [HttpClientDataSource] (HTTP) to
 * maintain a live, deduplicated map of discovered network devices.
 *
 * Lifecycle:
 *  - Call [startDiscovery] to open the multicast socket and begin scanning.
 *  - Call [stopDiscovery] when the feature is no longer visible.
 *  - Call [destroy] (from onCleared / Service.onDestroy) to cancel the scope.
 */
class DeviceRepositoryImpl(
    private val ssdpDataSource: SsdpDataSource,
    private val httpClientDataSource: HttpClientDataSource
) : DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepositoryImpl"
        private const val SEARCH_INTERVAL_MS = 30_000L
        private const val STALE_CHECK_MS = 60_000L
        private const val STALE_THRESHOLD_MS = 5 * 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Observable state ──────────────────────────────────────

    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    private val _isScanning = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)

    override val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // ── Active jobs ───────────────────────────────────────────

    private var listenerJob: Job? = null
    private var searchJob: Job? = null
    private var cleanupJob: Job? = null

    // ── DeviceRepository API ──────────────────────────────────

    override fun startDiscovery() {
        if (_isScanning.value) return
        Log.d(TAG, "Starting discovery")
        _isScanning.value = true
        _lastError.value = null

        startMulticastListener()
        startPeriodicSearch()
        startStaleCleanup()
    }

    override fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery")
        listenerJob?.cancel(); listenerJob = null
        searchJob?.cancel(); searchJob = null
        cleanupJob?.cancel(); cleanupJob = null
        _isScanning.value = false
    }

    override suspend fun refresh(): List<Device> {
        _devices.value = emptyMap()
        val results = ssdpDataSource.search("ssdp:all")
        results.forEach { (msg, srcIp) ->
            if (msg.type == SsdpMessageType.RESPONSE) processMessage(msg, srcIp)
        }
        return _devices.value.values.toList()
    }

    override fun clearDevices() {
        _devices.value = emptyMap()
    }

    /** Releases all resources — call from ViewModel.onCleared or Service.onDestroy. */
    fun destroy() {
        stopDiscovery()
        scope.cancel()
    }

    // ── Coroutine workers ─────────────────────────────────────

    private fun startMulticastListener() {
        listenerJob = scope.launch {
            try {
                ssdpDataSource.multicastMessages().collect { (msg, srcIp) ->
                    processMessage(msg, srcIp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multicast listener error", e)
                _lastError.value = "Discovery error: ${e.message}"
            }
        }
    }

    private fun startPeriodicSearch() {
        searchJob = scope.launch {
            // Immediate initial search
            sendSearches()

            while (isActive) {
                delay(SEARCH_INTERVAL_MS)
                sendSearches()
            }
        }
    }

    private suspend fun sendSearches() {
        ssdpDataSource.search("ssdp:all")
            .forEach { (msg, srcIp) -> processMessage(msg, srcIp) }
        delay(200)
        ssdpDataSource.search("urn:schemas-upnp-org:device:MediaServer:1")
            .forEach { (msg, srcIp) -> processMessage(msg, srcIp) }
    }

    private fun startStaleCleanup() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(STALE_CHECK_MS)
                val now = System.currentTimeMillis()
                val updated = _devices.value.filterValues { device ->
                    now - device.lastSeen <= STALE_THRESHOLD_MS
                }
                if (updated.size != _devices.value.size) {
                    val removed = _devices.value.size - updated.size
                    _devices.value = updated
                    Log.d(TAG, "Removed $removed stale device(s)")
                }
            }
        }
    }

    // ── Message processing ────────────────────────────────────

    private suspend fun processMessage(msg: SsdpMessageDto, srcIp: String) {
        // Handle byebye — remove from map
        if (msg.isByeBye) {
            msg.usn?.let { usn ->
                _devices.value = _devices.value.toMutableMap().also { it.remove(usn) }
                Log.d(TAG, "Device left: $usn")
            }
            return
        }

        // Build a stub device from the SSDP message
        val stub = msg.toDeviceStub(srcIp) ?: return

        // Skip bare UNKNOWN entries with no useful location
        if (stub.serverType == DeviceType.UNKNOWN && !stub.location.startsWith("http")) return

        // If we already know this device, just refresh the timestamp
        val existing = _devices.value[stub.usn]
        if (existing != null && existing.friendlyName.isNotEmpty()) {
            _devices.value = _devices.value.toMutableMap().also {
                it[stub.usn] = existing.refreshed()
            }
            return
        }

        // Enrich the stub by fetching the device description XML
        scope.launch {
            val enriched = httpClientDataSource
                .fetchDeviceDescription(stub.location)
                .getOrNull()
                ?.let { stub.enrichWithDescription(it) }
                ?: stub

            _devices.value = _devices.value.toMutableMap().also {
                it[enriched.usn] = enriched
            }
            Log.d(TAG, "Discovered: ${enriched.displayName} (${enriched.serverType})")
        }
    }
}