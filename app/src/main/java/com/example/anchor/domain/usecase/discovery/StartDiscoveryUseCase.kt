package com.example.anchor.domain.usecase.discovery

import com.example.anchor.domain.repository.DeviceRepository

/**
 * Starts continuous SSDP device discovery.
 */
class StartDiscoveryUseCase(private val repository: DeviceRepository) {
    operator fun invoke() {
        repository.startDiscovery()
    }
}