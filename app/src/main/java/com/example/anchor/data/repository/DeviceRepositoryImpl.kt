package com.example.anchor.data.repository

import android.util.Log
import com.example.anchor.core.config.AnchorConfig.Discovery.SEARCH_INTERVAL_MS
import com.example.anchor.core.config.AnchorConfig.Discovery.STALE_CHECK_MS
import com.example.anchor.core.config.AnchorConfig.Discovery.STALE_THRESHOLD_MS
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
import kotlinx.coroutines.supervisorScope

/**
 * Concrete implementation of [DeviceRepository].
 *
 * Orchestrates [SsdpDataSource] (UDP) and [HttpClientDataSource] (HTTP) to
 * maintain a live, deduplicated map of discovered network devices.
 */
class DeviceRepositoryImpl(
    private val ssdpDataSource: SsdpDataSource,
    private val httpClientDataSource: HttpClientDataSource
) : DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepositoryImpl"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Observable state ──────────────────────────────────────

    private val _devicesMap = mutableMapOf<String, Device>()
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
        clearDevices()
        val results = ssdpDataSource.search("ssdp:all")
        results.forEach { (msg, srcIp) ->
            if (msg.type == SsdpMessageType.RESPONSE) processMessage(msg, srcIp)
        }
        return _devices.value.values.toList()
    }

    override fun clearDevices() {
        synchronized(_devicesMap) {
            _devicesMap.clear()
            _devices.value = emptyMap()
        }
    }

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
                synchronized(_devicesMap) {
                    val initialSize = _devicesMap.size
                    val removed = _devicesMap.entries.removeIf { (_, device) ->
                        now - device.lastSeen > STALE_THRESHOLD_MS
                    }

                    if (removed) {
                        val removedCount = initialSize - _devicesMap.size
                        _devices.value = _devicesMap.toMap()
                        Log.d(TAG, "Removed $removedCount stale device(s)")
                    }
                }
            }
        }
    }

    // ── Message processing ────────────────────────────────────

    private suspend fun processMessage(msg: SsdpMessageDto, srcIp: String) {
        when {
            msg.isByeBye -> handleDeviceDeparture(msg)
            else -> handleDeviceAnnouncement(msg, srcIp)
        }
    }

    private fun handleDeviceDeparture(msg: SsdpMessageDto) {
        msg.usn?.let { usn ->
            removeDevice(usn)
            Log.d(TAG, "Device left: $usn")
        }
    }

    private suspend fun handleDeviceAnnouncement(msg: SsdpMessageDto, srcIp: String) {
        val stub = msg.toDeviceStub(srcIp) ?: return

        if (shouldSkipDevice(stub)) return

        val existing = findExistingDevice(stub.usn)
        if (existing != null && existing.isFullyEnriched()) {
            refreshDevice(existing)
            return
        }

        enrichAndAddDevice(stub)
    }

    private fun shouldSkipDevice(device: Device): Boolean {
        return device.serverType == DeviceType.UNKNOWN && 
               !device.location.startsWith("http")
    }

    private fun findExistingDevice(usn: String): Device? {
        return synchronized(_devicesMap) { _devicesMap[usn] }
    }

    private fun Device.isFullyEnriched(): Boolean {
        return friendlyName.isNotEmpty()
    }

    private fun refreshDevice(device: Device) {
        updateDevice(device.refreshed())
    }

    private suspend fun enrichAndAddDevice(stub: Device) {
        scope.launch {
            supervisorScope {
                val enriched = httpClientDataSource
                    .fetchDeviceDescription(stub.location)
                    .getOrNull()
                    ?.let { stub.enrichWithDescription(it) }
                    ?: stub

                updateDevice(enriched)
                Log.d(TAG, "Discovered: ${enriched.displayName} (${enriched.serverType})")
            }
        }
    }

    private fun updateDevice(device: Device) {
        synchronized(_devicesMap) {
            _devicesMap[device.usn] = device
            _devices.value = _devicesMap.toMap()
        }
    }

    private fun removeDevice(usn: String) {
        synchronized(_devicesMap) {
            if (_devicesMap.remove(usn) != null) {
                _devices.value = _devicesMap.toMap()
            }
        }
    }
}