// app/src/main/java/com/example/anchor/ui/screens/dashboard/DashboardViewModel.kt

package com.example.anchor.ui.dashboard

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.server.AnchorServerService
import com.example.anchor.server.AnchorServiceState
import com.example.anchor.server.LogEntry
import com.example.anchor.server.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DashboardUiState(
    val serverState: ServerState = ServerState(),
    val logs: List<LogEntry> = emptyList(),
    val sharedFolders: List<SharedFolder> = emptyList(),
    val isConnectedToWifi: Boolean = false,
    val showQrCode: Boolean = false,
    val selectedPort: Int = 8080
)

data class SharedFolder(
    val uri: String,
    val name: String,
    val path: String,
    val fileCount: Int = 0
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _sharedFolders = MutableStateFlow<List<SharedFolder>>(emptyList())
    private val _selectedPort = MutableStateFlow(8080)
    private val _showQrCode = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        AnchorServiceState.state,
        AnchorServiceState.logs,
        _sharedFolders,
        _selectedPort,
        _showQrCode
    ) { serverState, logs, folders, port, showQr ->
        DashboardUiState(
            serverState = serverState,
            logs = logs,
            sharedFolders = folders,
            isConnectedToWifi = NetworkUtils.isConnectedToWifi(application),
            showQrCode = showQr,
            selectedPort = port
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    fun addFolder(uri: Uri) {
        val context = getApplication<Application>()

        // Take persistable permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission might already be taken
        }

        val documentFile = DocumentFile.fromTreeUri(context, uri)
        val name = documentFile?.name ?: "Unknown"
        val path = uri.toString()

        // Count files in directory
        val fileCount = documentFile?.listFiles()?.count { it.isFile } ?: 0

        val folder = SharedFolder(
            uri = uri.toString(),
            name = name,
            path = path,
            fileCount = fileCount
        )

        _sharedFolders.update { current ->
            if (current.none { it.uri == folder.uri }) {
                current + folder
            } else {
                current
            }
        }
    }

    fun removeFolder(folder: SharedFolder) {
        _sharedFolders.update { current ->
            current.filter { it.uri != folder.uri }
        }
    }

    fun setPort(port: Int) {
        _selectedPort.value = port.coerceIn(1024, 65535)
    }

    fun toggleQrCode() {
        _showQrCode.update { !it }
    }

    fun startServer() {
        val context = getApplication<Application>()
        val folders = _sharedFolders.value

        // Convert document URIs to file paths where possible
        val directories = folders.mapNotNull { folder ->
            try {
                // For content URIs, we need to work with DocumentFile
                // The server will need to handle these appropriately
                getPathFromUri(folder.uri)
            } catch (e: Exception) {
                null
            }
        }

        AnchorServerService.startServer(
            context = context,
            port = _selectedPort.value,
            directories = ArrayList(directories)
        )
    }

    fun stopServer() {
        val context = getApplication<Application>()
        AnchorServerService.stopServer(context)
    }

    fun clearLogs() {
        AnchorServiceState.clearLogs()
    }

    private fun getPathFromUri(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)

            // Try to get actual file path for file:// URIs
            if (uri.scheme == "file") {
                return uri.path
            }

            // For content:// URIs, try to resolve path
            // This is a simplified approach - in production you'd use SAF properly
            val segments = uri.pathSegments
            if (segments.isNotEmpty()) {
                val lastSegment = segments.last()
                if (lastSegment.contains(":")) {
                    val path = lastSegment.substringAfter(":")
                    "/storage/emulated/0/$path"
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}