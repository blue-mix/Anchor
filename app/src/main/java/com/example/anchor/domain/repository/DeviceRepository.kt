package com.example.anchor.domain.repository

import com.example.anchor.domain.model.Device
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for UPnP/SSDP device discovery.
 *
 * The implementation ([DeviceRepositoryImpl]) uses StateFlow so callers
 * can read the current value synchronously via .value without collecting.
 * [DiscoveryViewModel] depends only on this interface so it can be tested
 * with a fake/mock without spinning up real sockets.
 */
interface DeviceRepository {

    /**
     * Hot flow that emits the current map of discovered devices whenever
     * a device is added, updated, or removed.
     *
     * Key = USN (Unique Service Name), Value = [Device].
     */
    val devices: StateFlow<Map<String, Device>>

    /**
     * Emits true while an active M-SEARCH or multicast listener is running.
     */
    val isScanning: StateFlow<Boolean>

    /**
     * Emits the last error message from the discovery layer, or null when healthy.
     */
    val lastError: StateFlow<String?>

    /** Starts continuous SSDP discovery (multicast listener + periodic M-SEARCH). */
    fun startDiscovery()

    /** Stops discovery and releases the multicast lock. */
    fun stopDiscovery()

    /**
     * Clears all discovered devices, sends a fresh M-SEARCH, and waits for
     * responses.  Returns the refreshed device list.
     */
    suspend fun refresh(): List<Device>

    /** Drops all cached devices from the in-memory map. */
    fun clearDevices()
}