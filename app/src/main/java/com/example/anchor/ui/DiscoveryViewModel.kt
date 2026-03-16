// app/src/main/java/com/example/anchor/ui/screens/discovery/DiscoveryViewModel.kt

package com.example.anchor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.data.UpnpDiscoveryManager
import com.example.anchor.data.model.DiscoveredDevice
import com.example.anchor.data.model.ServerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val filterType: ServerTypeFilter = ServerTypeFilter.ALL
)

enum class ServerTypeFilter {
    ALL, ANCHOR_ONLY, MEDIA_SERVERS, RENDERERS
}

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {

    private val discoveryManager = UpnpDiscoveryManager(application)

    private val _filterType = MutableStateFlow(ServerTypeFilter.ALL)
    val filterType: StateFlow<ServerTypeFilter> = _filterType.asStateFlow()

    val uiState: StateFlow<DiscoveryUiState> = combine(
        discoveryManager.discoveredDevices,
        discoveryManager.isScanning,
        discoveryManager.lastError,
        _filterType
    ) { devices, isScanning, error, filter ->
        val filteredDevices = filterDevices(devices.values.toList(), filter)
        DiscoveryUiState(
            devices = filteredDevices.sortedByDescending { it.lastSeen },
            isScanning = isScanning,
            errorMessage = error,
            filterType = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiscoveryUiState()
    )

    init {
        // Start discovery when ViewModel is created
        startDiscovery()
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            discoveryManager.clearDevices()
            discoveryManager.performSingleScan()
        }
    }

    fun setFilter(filter: ServerTypeFilter) {
        _filterType.value = filter
    }

    private fun filterDevices(
        devices: List<DiscoveredDevice>,
        filter: ServerTypeFilter
    ): List<DiscoveredDevice> {
        return when (filter) {
            ServerTypeFilter.ALL -> devices
            ServerTypeFilter.ANCHOR_ONLY -> devices.filter { it.serverType == ServerType.ANCHOR }
            ServerTypeFilter.MEDIA_SERVERS -> devices.filter {
                it.serverType == ServerType.ANCHOR || it.serverType == ServerType.DLNA_MEDIA_SERVER
            }

            ServerTypeFilter.RENDERERS -> devices.filter { it.serverType == ServerType.DLNA_RENDERER }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.destroy()
    }
}