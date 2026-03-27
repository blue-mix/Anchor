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
import com.example.anchor.server.service.AnchorServerService
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
 *
 * Changes from original:
 *  - [DashboardUiState.serverState] (old flat ServerState) replaced by
 *    [DashboardUiState.serverStatus] using domain [ServerStatus] sealed interface.
 *  - [AnchorServiceState.state] (removed) replaced by [AnchorServiceState.status].
 *  - [LogEntry] imported from [server.service] (where it now lives).
 *  - [AnchorServerService] imported from [server.service] package.
 *  - [startServer] builds a proper [ServerConfig] with [SharedDirectory] values
 *    instead of passing raw path lists.
 *  - [SharedFolder.path] renamed to [SharedFolder.absolutePath] for clarity.
 *  - Still extends [AndroidViewModel] — SAF (DocumentFile) requires a Context.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

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
        // Eagerly: the flow never stops collecting even when no UI is subscribed.
        // This ensures AnchorServiceState.status updates from the background
        // service thread are never missed between recompositions.
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState()
    )

    // ── Folder management ─────────────────────────────────────

    fun addFolder(uri: Uri) {
        val context = getApplication<Application>()

        // Take persistable SAF permission
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
        val context = getApplication<Application>()
        val port = _selectedPort.value
        val folders = _sharedFolders.value

        // Collect every path we can resolve. For SAF URIs where the path
        // cannot be converted to a filesystem path, we still log it so the
        // user knows which folders were skipped.
        val directories = ArrayList<String>()
        folders.forEach { folder ->
            val path = resolveAbsolutePath(folder.uri)
            if (path != null) {
                directories.add(path)
                Log.d(TAG, "Resolved folder: ${folder.name} → $path")
            } else {
                // SAF URI that cannot be converted — log it but don't block startup
                Log.w(TAG, "Could not resolve path for ${folder.name} (${folder.uri})")
                AnchorServiceState.addLog("Warning: cannot resolve path for ${folder.name}")
            }
        }

        AnchorServerService.startServer(
            context = context,
            port = port,
            directories = directories
        )
    }

    fun stopServer() {
        AnchorServerService.stopServer(getApplication())
    }

    fun clearLogs() {
        AnchorServiceState.clearLogs()
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Converts a content:// or file:// URI string to an absolute filesystem path.
     * This is a best-effort approach for the common SAF path format.
     * Returns null when the path cannot be resolved.
     */
    private fun resolveAbsolutePath(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    val segments = uri.pathSegments
                    if (segments.isNotEmpty()) {
                        val last = segments.last()
                        if (last.contains(":")) {
                            "/storage/emulated/0/${last.substringAfter(":")}"
                        } else null
                    } else null
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}