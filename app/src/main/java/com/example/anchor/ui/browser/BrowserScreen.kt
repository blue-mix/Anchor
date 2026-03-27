package com.example.anchor.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.anchor.data.dto.MediaFileDto
import com.example.anchor.domain.model.MediaType
import org.koin.androidx.compose.koinViewModel

/**
 * Remote browser screen — navigates a remote Anchor server's file tree.
 *
 * Changes from original:
 *  - [MediaFile] (removed data.model type) → [MediaFileDto] from data.dto.
 *  - [MediaType] imported from domain.model (enum); [MediaFileDto.mediaType]
 *    is a String so we parse it with [mediaTypeOf] at call sites.
 *  - [file.formattedSize] (was a property on the old MediaFile) replaced by
 *    [MediaFileDto.formattedSize] extension function defined at the bottom of
 *    this file — keeps the UI helper local and avoids polluting the DTO.
 *  - [viewModel()] → [koinViewModel()].
 *  - [handleFileClick] updated to parse the mediaType string before comparing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(
    deviceName: String,
    baseUrl: String,
    onNavigateBack: () -> Unit,
    onPlayMedia: (url: String, title: String, mimeType: String) -> Unit,
    viewModel: RemoteBrowserViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(baseUrl) { viewModel.initialize(baseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = deviceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.currentPath.isNotEmpty()) {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.currentPath.isNotEmpty()) viewModel.navigateUp()
                            else onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.viewMode == ViewMode.LIST)
                                Icons.Rounded.GridView else Icons.Rounded.ViewList,
                            contentDescription = "Toggle View"
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.refresh() }
                    )
                }

                uiState.currentPath.isEmpty() && uiState.directories.isNotEmpty() -> {
                    DirectorySelector(
                        directories = uiState.directories,
                        onDirectorySelected = { viewModel.browsePath(it.path) }
                    )
                }

                uiState.files.isEmpty() -> EmptyState()
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.LIST -> FileListView(
                            files = uiState.files,
                            onFileClick = { handleFileClick(it, viewModel, onPlayMedia) },
                            getThumbnailUrl = { viewModel.getThumbnailUrl(it) }
                        )

                        ViewMode.GRID -> FileGridView(
                            files = uiState.files,
                            onFileClick = { handleFileClick(it, viewModel, onPlayMedia) },
                            getThumbnailUrl = { viewModel.getThumbnailUrl(it) }
                        )
                    }
                }
            }
        }
    }
}

// ── Click handling ────────────────────────────────────────────

private fun handleFileClick(
    file: MediaFileDto,
    viewModel: RemoteBrowserViewModel,
    onPlayMedia: (String, String, String) -> Unit
) {
    when {
        file.isDirectory -> viewModel.browsePath(file.path)
        file.mediaTypeOf() == MediaType.VIDEO ||
                file.mediaTypeOf() == MediaType.AUDIO ->
            onPlayMedia(viewModel.getStreamUrl(file), file.name, file.mimeType)
    }
}

// ── Directory selector ────────────────────────────────────────

@Composable
private fun DirectorySelector(
    directories: List<DirectoryInfo>,
    onDirectorySelected: (DirectoryInfo) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Select a folder to browse",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(directories) { directory ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDirectorySelected(directory) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(directory.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            directory.alias,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── List view ─────────────────────────────────────────────────

@Composable
private fun FileListView(
    files: List<MediaFileDto>,
    onFileClick: (MediaFileDto) -> Unit,
    getThumbnailUrl: (MediaFileDto) -> String
) {
    LazyColumn {
        items(files, key = { it.path }) { file ->
            FileListItem(
                file = file,
                thumbnailUrl = getThumbnailUrl(file),
                onClick = { onFileClick(file) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FileListItem(
    file: MediaFileDto,
    thumbnailUrl: String,
    onClick: () -> Unit
) {
    val mediaType = file.mediaTypeOf()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            when {
                file.isDirectory -> Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                mediaType == MediaType.VIDEO || mediaType == MediaType.IMAGE -> AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                else -> Icon(
                    imageVector = getFileIcon(mediaType),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!file.isDirectory) {
                Text(
                    text = file.formattedSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Grid view ─────────────────────────────────────────────────

@Composable
private fun FileGridView(
    files: List<MediaFileDto>,
    onFileClick: (MediaFileDto) -> Unit,
    getThumbnailUrl: (MediaFileDto) -> String
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { it.path }) { file ->
            FileGridItem(
                file = file,
                thumbnailUrl = getThumbnailUrl(file),
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
private fun FileGridItem(
    file: MediaFileDto,
    thumbnailUrl: String,
    onClick: () -> Unit
) {
    val mediaType = file.mediaTypeOf()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    file.isDirectory -> Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    mediaType == MediaType.VIDEO || mediaType == MediaType.IMAGE -> AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    else -> Icon(
                        imageVector = getFileIcon(mediaType),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!file.isDirectory) {
                    Text(
                        text = file.formattedSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Empty / error states ──────────────────────────────────────

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("This folder is empty", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun getFileIcon(mediaType: MediaType): ImageVector = when (mediaType) {
    MediaType.VIDEO -> Icons.Rounded.VideoFile
    MediaType.AUDIO -> Icons.Rounded.AudioFile
    MediaType.IMAGE -> Icons.Rounded.Image
    MediaType.DOCUMENT -> Icons.Rounded.InsertDriveFile
    MediaType.UNKNOWN -> Icons.Rounded.InsertDriveFile
}

/**
 * Parses the [MediaFileDto.mediaType] string to the domain [MediaType] enum.
 * Falls back to [MediaType.UNKNOWN] for any unrecognised value.
 */
private fun MediaFileDto.mediaTypeOf(): MediaType =
    runCatching { MediaType.valueOf(mediaType.uppercase()) }.getOrDefault(MediaType.UNKNOWN)

/**
 * Human-readable file size for display — mirrors the property on domain [MediaItem]
 * but kept here as a local extension so the DTO stays annotation-only.
 */
private fun MediaFileDto.formattedSize(): String = when {
    size < 1_024L -> "$size B"
    size < 1_048_576L -> "%.1f KB".format(size / 1_024.0)
    size < 1_073_741_824L -> "%.1f MB".format(size / 1_048_576.0)
    else -> "%.2f GB".format(size / 1_073_741_824.0)
}