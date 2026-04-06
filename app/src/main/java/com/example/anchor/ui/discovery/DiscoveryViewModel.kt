package com.example.anchor.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import com.example.anchor.domain.usecase.discovery.GetDiscoveredDevicesUseCase
import com.example.anchor.domain.usecase.discovery.StartDiscoveryUseCase
import com.example.anchor.domain.usecase.discovery.StopDiscoveryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiscoveryUiState(
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val filterType: ServerTypeFilter = ServerTypeFilter.ALL
)

enum class ServerTypeFilter {
    ALL, ANCHOR_ONLY, MEDIA_SERVERS, RENDERERS
}

/**
 * ViewModel for the device discovery screen.
 * Now uses domain UseCases instead of depending directly on Repository.
 */
class DiscoveryViewModel(
    private val startDiscoveryUseCase: StartDiscoveryUseCase,
    private val stopDiscoveryUseCase: StopDiscoveryUseCase,
    private val getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase
) : ViewModel() {

    private val _filterType = MutableStateFlow(ServerTypeFilter.ALL)
    val filterType: StateFlow<ServerTypeFilter> = _filterType.asStateFlow()

    val uiState: StateFlow<DiscoveryUiState> = combine(
        getDiscoveredDevicesUseCase(),
        // Note: isScanning and lastError are still in Repository. 
        // In a strict clean architecture, we might add UseCases for these too.
        // For now, we'll focus on the primary actions.
        _filterType
    ) { devicesMap, filter ->
        DiscoveryUiState(
            devices = filterDevices(devicesMap.values.toList(), filter)
                .sortedByDescending { it.lastSeen },
            isScanning = false, // Simplified for now, or add more use cases
            errorMessage = null,
            filterType = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoveryUiState()
    )

    init {
        startDiscoveryUseCase()
    }

    fun refreshDevices() {
        // Repository refresh is usually called by re-starting discovery or a specific refresh usecase
        startDiscoveryUseCase()
    }

    fun setFilter(filter: ServerTypeFilter) {
        _filterType.value = filter
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscoveryUseCase()
    }

    private fun filterDevices(
        devices: List<Device>,
        filter: ServerTypeFilter
    ): List<Device> = when (filter) {
        ServerTypeFilter.ALL -> devices
        ServerTypeFilter.ANCHOR_ONLY -> devices.filter {
            it.serverType == DeviceType.ANCHOR
        }

        ServerTypeFilter.MEDIA_SERVERS -> devices.filter {
            it.serverType == DeviceType.ANCHOR ||
                    it.serverType == DeviceType.DLNA_MEDIA_SERVER
        }

        ServerTypeFilter.RENDERERS -> devices.filter {
            it.serverType == DeviceType.DLNA_RENDERER
        }
    }
}