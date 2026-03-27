package com.example.anchor.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.data.dto.DirectoryListingDto
import com.example.anchor.data.dto.MediaFileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URL

data class RemoteBrowserUiState(
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val parentPath: String? = null,
    val files: List<MediaFileDto> = emptyList(),
    val directories: List<DirectoryInfo> = emptyList(),
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val selectedDirectory: String? = null
)

data class DirectoryInfo(
    val alias: String,
    val name: String,
    val path: String
)

enum class ViewMode { LIST, GRID }

/**
 * ViewModel for browsing a remote Anchor server's file library.
 *
 * Changes from original:
 *  - [RemoteBrowserUiState.files] now holds [MediaFileDto] (the serialisable
 *    DTO) instead of the old [MediaFile] / [data.model.MediaFile].
 *    This ViewModel fetches JSON from a remote server and deserialises it,
 *    so DTOs are the right type here — domain [MediaItem] is for local logic.
 *  - [DirectoryListingDto.files] replaces the old [DirectoryListing.files]
 *    (field was already named "files" in the DTO).
 *  - No longer uses the removed [data.model.DirectoryListing] or
 *    [data.model.MediaFile] classes.
 *  - URL helpers use [MediaFileDto.path] which is already present.
 *  - No other logic changes.
 */
class RemoteBrowserViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(RemoteBrowserUiState())
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    private var baseUrl: String = ""

    fun initialize(baseUrl: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        loadDirectories()
    }

    // ── Directory loading ─────────────────────────────────────

    private fun loadDirectories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = fetchUrl("$baseUrl/api/directories")
                val directories = json.decodeFromString<List<Map<String, String>>>(response)
                    .map { map ->
                        DirectoryInfo(
                            alias = map["alias"] ?: "",
                            name = map["name"] ?: "",
                            path = map["path"] ?: ""
                        )
                    }

                _uiState.update { it.copy(isLoading = false, directories = directories) }

                // Auto-navigate when there is only one shared directory
                if (directories.size == 1) browsePath(directories.first().path)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load directories: ${e.message}"
                    )
                }
            }
        }
    }

    // ── Path browsing ─────────────────────────────────────────

    fun browsePath(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val apiPath = path.trimStart('/')
                val response = fetchUrl("$baseUrl/api/browse/$apiPath")
                val listing = json.decodeFromString<DirectoryListingDto>(response)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = listing.path,
                        parentPath = listing.parentPath,
                        files = listing.files,
                        selectedDirectory = path.split("/").getOrNull(1)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load directory: ${e.message}"
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val parentPath = _uiState.value.parentPath
        if (parentPath != null) {
            browsePath(parentPath)
        } else {
            _uiState.update {
                it.copy(currentPath = "", files = emptyList(), selectedDirectory = null)
            }
        }
    }

    fun refresh() {
        val current = _uiState.value.currentPath
        if (current.isNotEmpty()) browsePath(current) else loadDirectories()
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    // ── URL builders ──────────────────────────────────────────

    fun getFileUrl(file: MediaFileDto): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/files/$filePath"
    }

    fun getStreamUrl(file: MediaFileDto): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/stream/$filePath"
    }

    fun getThumbnailUrl(file: MediaFileDto): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/thumbnail/$filePath"
    }

    // ── Network ───────────────────────────────────────────────

    private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        URL(url).readText()
    }
}