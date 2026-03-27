package com.example.anchor.domain.usecase.discovery

import com.example.anchor.domain.repository.DeviceRepository

/**
 * Stops continuous SSDP device discovery.
 */
class StopDiscoveryUseCase(private val repository: DeviceRepository) {
    operator fun invoke() {
        repository.stopDiscovery()
    }
}