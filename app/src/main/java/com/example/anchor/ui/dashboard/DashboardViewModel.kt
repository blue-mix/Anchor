package com.example.anchor.ui.dashboard

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.domain.model.ServerConfig
import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.domain.model.SharedDirectory
import com.example.anchor.server.ServerController
import com.example.anchor.server.service.AnchorServiceState
import com.example.anchor.server.service.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DashboardUiState(
    val serverStatus: ServerStatus = ServerStatus.Stopped,
    val logs: List<LogEntry> = emptyList(),
    val sharedFolders: List<SharedFolder> = emptyList(),
    val isConnectedToWifi: Boolean = false,
    val showQrCode: Boolean = false,
    val selectedPort: Int = ServerConfig.DEFAULT_PORT
)

data class SharedFolder(
    val uri: String,
    val name: String,
    val absolutePath: String,
    val fileCount: Int = 0
)

/**
 * ViewModel for the server dashboard screen.
 */
class DashboardViewModel(
    application: Application,
    private val serverController: ServerController
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val _sharedFolders = MutableStateFlow<List<SharedFolder>>(emptyList())
    private val _selectedPort = MutableStateFlow(ServerConfig.DEFAULT_PORT)
    private val _showQrCode = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> = combine(
        AnchorServiceState.status,
        AnchorServiceState.logs,
        _sharedFolders,
        _selectedPort,
        _showQrCode
    ) { status, logs, folders, port, showQr ->
        DashboardUiState(
            serverStatus = status,
            logs = logs,
            sharedFolders = folders,
            isConnectedToWifi = NetworkUtils.isConnectedToWifi(application),
            showQrCode = showQr,
            selectedPort = port
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState()
    )

    // ── Folder management ─────────────────────────────────────

    fun addFolder(uri: Uri) {
        val context = getApplication<Application>()

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val docFile = DocumentFile.fromTreeUri(context, uri)
        val name = docFile?.name ?: "Unknown"
        val path = resolveAbsolutePath(uri.toString()) ?: uri.toString()
        val fileCount = docFile?.listFiles()?.count { it.isFile } ?: 0

        val folder = SharedFolder(
            uri = uri.toString(),
            name = name,
            absolutePath = path,
            fileCount = fileCount
        )

        _sharedFolders.update { current ->
            if (current.none { it.uri == folder.uri }) current + folder else current
        }
    }

    fun removeFolder(folder: SharedFolder) {
        _sharedFolders.update { it.filter { f -> f.uri != folder.uri } }
    }

    fun setPort(port: Int) {
        _selectedPort.value = port.coerceIn(ServerConfig.MIN_PORT, ServerConfig.MAX_PORT)
    }

    fun toggleQrCode() {
        _showQrCode.update { !it }
    }

    // ── Server control ────────────────────────────────────────

    fun startServer() {
        val port = _selectedPort.value
        val folders = _sharedFolders.value

        val sharedDirsMap = folders.mapNotNull { folder ->
            val path = resolveAbsolutePath(folder.uri)
            if (path != null) {
                val alias = folder.name.lowercase().replace(" ", "_")
                alias to SharedDirectory(
                    alias = alias,
                    displayName = folder.name,
                    absolutePath = path,
                    fileCount = folder.fileCount
                )
            } else {
                Log.w(TAG, "Could not resolve path for ${folder.name}")
                AnchorServiceState.addLog("Warning: cannot resolve path for ${folder.name}")
                null
            }
        }.toMap()

        val config = ServerConfig(
            port = port,
            sharedDirectories = sharedDirsMap
        )

        serverController.start(config)
    }

    fun stopServer() {
        serverController.stop()
    }

    fun clearLogs() {
        AnchorServiceState.clearLogs()
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun resolveAbsolutePath(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    val last = uri.pathSegments.lastOrNull()
                    if (last?.contains(":") == true) {
                        "/storage/emulated/0/${last.substringAfter(":")}"
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
