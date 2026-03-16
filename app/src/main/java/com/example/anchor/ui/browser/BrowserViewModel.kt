// app/src/main/java/com/example/anchor/ui/screens/browser/RemoteBrowserViewModel.kt

package com.example.anchor.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anchor.data.model.DirectoryListing
import com.example.anchor.data.model.MediaFile

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
    val files: List<MediaFile> = emptyList(),
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

class RemoteBrowserViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(RemoteBrowserUiState())
    val uiState: StateFlow<RemoteBrowserUiState> = _uiState.asStateFlow()

    private var baseUrl: String = ""

    fun initialize(baseUrl: String) {
        this.baseUrl = baseUrl.trimEnd('/')
        loadDirectories()
    }

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

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        directories = directories
                    )
                }

                // Auto-navigate to first directory if only one exists
                if (directories.size == 1) {
                    browsePath(directories.first().path)
                }
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

    fun browsePath(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val apiPath = path.trimStart('/')
                val response = fetchUrl("$baseUrl/api/browse/$apiPath")
                val listing = json.decodeFromString<DirectoryListing>(response)

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
            // Go back to directory selection
            _uiState.update {
                it.copy(
                    currentPath = "",
                    files = emptyList(),
                    selectedDirectory = null
                )
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun refresh() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty()) {
            browsePath(currentPath)
        } else {
            loadDirectories()
        }
    }

    fun getFileUrl(file: MediaFile): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/files/$filePath"
    }

    fun getStreamUrl(file: MediaFile): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/stream/$filePath"
    }

    fun getThumbnailUrl(file: MediaFile): String {
        val filePath = file.path.trimStart('/')
        return "$baseUrl/thumbnail/$filePath"
    }

    private suspend fun fetchUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            URL(url).readText()
        }
    }
}