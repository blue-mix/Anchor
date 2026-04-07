package com.example.anchor.presentation.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import org.koin.androidx.compose.koinViewModel

/**
 * Discovery screen — shows UPnP/SSDP devices found on the LAN.
 *
 * Changes from original:
 *  - [DiscoveredDevice] → [Device] (domain model).
 *  - [ServerType] → [DeviceType] (domain enum).
 *  - [viewModel()] → [koinViewModel()] — [DiscoveryViewModel] no longer
 *    extends AndroidViewModel so Compose's default factory can't construct it;
 *    Koin's factory resolves the [DeviceRepository] dependency.
 *  - [onDeviceClick] callback type updated to [Device].
 *  - [ServerTypeBadge], [getDeviceIcon], [getDeviceIconTint] all switch on
 *    [DeviceType] instead of the removed [ServerType].
 *  - [DisposableEffect] removed — the ViewModel starts/stops discovery in
 *    init / onCleared, so the screen doesn't need to manage that lifecycle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onDeviceClick: (Device) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: DiscoveryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover Devices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { viewModel.refreshDevices() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FilterChipRow(
                currentFilter = uiState.filterType,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            if (uiState.devices.isEmpty()) {
                EmptyDiscoveryState(
                    isScanning = uiState.isScanning,
                    errorMessage = uiState.errorMessage
                )
            } else {
                DeviceList(
                    devices = uiState.devices,
                    onDeviceClick = onDeviceClick
                )
            }
        }
    }
}

// ── Filter chips ──────────────────────────────────────────────

@Composable
private fun FilterChipRow(
    currentFilter: ServerTypeFilter,
    onFilterSelected: (ServerTypeFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentFilter == ServerTypeFilter.ALL,
            onClick = { onFilterSelected(ServerTypeFilter.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = currentFilter == ServerTypeFilter.MEDIA_SERVERS,
            onClick = { onFilterSelected(ServerTypeFilter.MEDIA_SERVERS) },
            label = { Text("Servers") }
        )
        FilterChip(
            selected = currentFilter == ServerTypeFilter.RENDERERS,
            onClick = { onFilterSelected(ServerTypeFilter.RENDERERS) },
            label = { Text("TVs") }
        )
    }
}

// ── Device list ───────────────────────────────────────────────

@Composable
private fun DeviceList(
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = devices, key = { it.usn }) { device ->
            DeviceCard(device = device, onClick = { onDeviceClick(device) })
        }
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getDeviceIcon(device.serverType),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = getDeviceIconTint(device.serverType)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(device.ipAddress)
                        if (device.port > 0) append(":${device.port}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.manufacturer.isNotEmpty()) {
                    Text(
                        text = device.manufacturer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ServerTypeBadge(serverType = device.serverType)
        }
    }
}

// ── Type badge ────────────────────────────────────────────────

@Composable
private fun ServerTypeBadge(serverType: DeviceType) {
    val (text, color) = when (serverType) {
        DeviceType.ANCHOR -> "Anchor" to MaterialTheme.colorScheme.primary
        DeviceType.DLNA_MEDIA_SERVER -> "DLNA" to MaterialTheme.colorScheme.secondary
        DeviceType.DLNA_RENDERER -> "TV" to MaterialTheme.colorScheme.tertiary
        DeviceType.UNKNOWN -> "Other" to MaterialTheme.colorScheme.outline
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// ── Empty state ───────────────────────────────────────────────

@Composable
private fun EmptyDiscoveryState(isScanning: Boolean, errorMessage: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(visible = isScanning, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning network…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            AnimatedVisibility(
                visible = !isScanning && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.DeviceHub,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No devices found", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Make sure other devices are on the same Wi-Fi network",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Discovery Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Icon helpers ──────────────────────────────────────────────

@Composable
private fun getDeviceIcon(serverType: DeviceType): ImageVector = when (serverType) {
    DeviceType.ANCHOR -> Icons.Rounded.PhoneAndroid
    DeviceType.DLNA_MEDIA_SERVER -> Icons.Rounded.Storage
    DeviceType.DLNA_RENDERER -> Icons.Rounded.Tv
    DeviceType.UNKNOWN -> Icons.Rounded.Computer
}

@Composable
private fun getDeviceIconTint(serverType: DeviceType) = when (serverType) {
    DeviceType.ANCHOR -> MaterialTheme.colorScheme.primary
    DeviceType.DLNA_MEDIA_SERVER -> MaterialTheme.colorScheme.secondary
    DeviceType.DLNA_RENDERER -> MaterialTheme.colorScheme.tertiary
    DeviceType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}