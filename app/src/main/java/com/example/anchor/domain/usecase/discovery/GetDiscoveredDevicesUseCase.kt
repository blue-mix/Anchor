package com.example.anchor.domain.usecase.discovery

import com.example.anchor.domain.model.Device
import com.example.anchor.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Use case for observing the list of discovered UPnP devices.
 */
class GetDiscoveredDevicesUseCase(private val repository: DeviceRepository) {
    operator fun invoke(): StateFlow<Map<String, Device>> {
        return repository.devices
    }
}