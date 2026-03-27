package com.example.anchor.ui.dashboard

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.server.service.LogEntry
import com.example.anchor.server.service.LogLevel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard screen — server control, folder management, console logs.
 *
 * Changes from original:
 *  - [LogEntry] and [LogLevel] imported from [server.service] package
 *    (moved from the old flat [server] package).
 *  - All references to the old flat [ServerState] fields replaced by
 *    pattern-matching on [ServerStatus]:
 *      uiState.serverState.isRunning    → uiState.serverStatus is ServerStatus.Running
 *      uiState.serverState.serverUrl    → (uiState.serverStatus as? ServerStatus.Running)?.url
 *      uiState.serverState.localIpAddress → (uiState.serverStatus as? ServerStatus.Running)?.ipAddress
 *  - [SharedFolder.path] → [SharedFolder.absolutePath].
 *  - [viewModel()] → [koinViewModel()].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToDiscovery: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.addFolder(it) } }

    // Derive server fields from sealed ServerStatus
    val isRunning = uiState.serverStatus is ServerStatus.Running
    val runningState = uiState.serverStatus as? ServerStatus.Running
    val serverUrl = runningState?.url
    val ipAddress = runningState?.ipAddress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anchor") },
                actions = {
                    IconButton(onClick = onNavigateToDiscovery) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Discover Devices"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { folderPickerLauncher.launch(null) }) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Folder")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NetworkStatusCard(
                    isConnected = uiState.isConnectedToWifi,
                    ipAddress = ipAddress,
                    serverUrl = serverUrl
                )
            }
            item {
                ServerControlCard(
                    serverStatus = uiState.serverStatus,
                    isRunning = isRunning,
                    serverUrl = serverUrl,
                    port = uiState.selectedPort,
                    onPortChange = { viewModel.setPort(it) },
                    onStartServer = { viewModel.startServer() },
                    onStopServer = { viewModel.stopServer() },
                    onShowQrCode = { viewModel.toggleQrCode() },
                    onCopyUrl = {
                        serverUrl?.let { clipboardManager.setText(AnnotatedString(it)) }
                    },
                    hasSharedFolders = uiState.sharedFolders.isNotEmpty()
                )
            }
            item {
                SharedFoldersSection(
                    folders = uiState.sharedFolders,
                    onAddFolder = { folderPickerLauncher.launch(null) },
                    onRemoveFolder = { viewModel.removeFolder(it) }
                )
            }
            item {
                ConsoleLogsSection(
                    logs = uiState.logs,
                    onClearLogs = { viewModel.clearLogs() }
                )
            }
        }
    }

    if (uiState.showQrCode && serverUrl != null) {
        QrCodeDialog(url = serverUrl, onDismiss = { viewModel.toggleQrCode() })
    }
}

// ── Network status ────────────────────────────────────────────

@Composable
private fun NetworkStatusCard(
    isConnected: Boolean,
    ipAddress: String?,
    serverUrl: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) "Connected to Wi-Fi" else "Not Connected",
                    style = MaterialTheme.typography.titleMedium
                )
                if (isConnected && ipAddress != null) {
                    Text(
                        text = "IP: $ipAddress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Server control ────────────────────────────────────────────

@Composable
private fun ServerControlCard(
    serverStatus: ServerStatus,
    isRunning: Boolean,
    serverUrl: String?,
    port: Int,
    onPortChange: (Int) -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onShowQrCode: () -> Unit,
    onCopyUrl: () -> Unit,
    hasSharedFolders: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Media Server", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (isRunning) "Running" else "Stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isRunning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(visible = !isRunning) {
                Column {
                    var portText by remember(port) { mutableStateOf(port.toString()) }
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { v ->
                            portText = v
                            v.toIntOrNull()?.let { onPortChange(it) }
                        },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            AnimatedVisibility(visible = isRunning && serverUrl != null) {
                Column {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = serverUrl ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCopyUrl) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy URL")
                            }
                            IconButton(onClick = onShowQrCode) {
                                Icon(Icons.Rounded.QrCode, contentDescription = "Show QR Code")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Button(
                onClick = { if (isRunning) onStopServer() else onStartServer() },
                modifier = Modifier.fillMaxWidth(),
                enabled = serverStatus !is ServerStatus.Starting,
                colors = if (isRunning)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors()
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (serverStatus) {
                        is ServerStatus.Starting -> "Starting…"
                        is ServerStatus.Running -> "Stop Server"
                        else -> "Start Server"
                    }
                )
            }

            if (!hasSharedFolders && !isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add at least one folder to start the server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ── Shared folders ────────────────────────────────────────────

@Composable
private fun SharedFoldersSection(
    folders: List<SharedFolder>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (SharedFolder) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shared Folders", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${folders.size} folders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (folders.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onAddFolder) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No folders shared", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Tap to add a folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(folders, key = { it.uri }) { folder ->
                    FolderCard(folder = folder, onRemove = { onRemoveFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: SharedFolder, onRemove: () -> Unit) {
    Card(modifier = Modifier.width(160.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove folder",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.fileCount} files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Console logs ──────────────────────────────────────────────

@Composable
private fun ConsoleLogsSection(logs: List<LogEntry>, onClearLogs: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Console", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClearLogs) {
                Icon(Icons.Rounded.Delete, contentDescription = "Clear logs")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No logs yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    reverseLayout = true
                ) {
                    items(logs.reversed()) { entry -> LogEntryItem(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── QR code dialog ────────────────────────────────────────────

@Composable
private fun QrCodeDialog(url: String, onDismiss: () -> Unit) {
    val qrBitmap = remember(url) { generateQrCode(url) }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scan to Connect", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(240.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

private fun generateQrCode(text: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}