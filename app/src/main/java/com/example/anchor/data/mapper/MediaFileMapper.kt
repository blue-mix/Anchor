package com.example.anchor.data.mapper

import com.example.anchor.data.dto.DirectoryListingDto
import com.example.anchor.data.dto.MediaFileDto
import com.example.anchor.domain.model.Alias
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.model.MediaPath
import com.example.anchor.domain.model.MediaType
import com.example.anchor.domain.model.MimeType
import java.io.File

/**
 * Maps between [MediaFileDto] / [DirectoryListingDto] (data layer wire types)
 * and [MediaItem] / [DirectoryListing] (domain layer types).
 */
object MediaFileMapper {

    // ── DTO → Domain ──────────────────────────────────────────

    /** Converts a [MediaFileDto] received from a remote Anchor server. */
    fun MediaFileDto.toDomain(): MediaItem = MediaItem(
        name = name,
        path = MediaPath(path),
        absolutePath = absolutePath,
        isDirectory = isDirectory,
        size = size,
        mimeType = MimeType(mimeType),
        lastModified = lastModified,
        mediaType = MediaType.valueOf(mediaType.uppercase().let {
            if (MediaType.entries.any { e -> e.name == it }) it else "UNKNOWN"
        })
    )

    /** Converts a [DirectoryListingDto] received from a remote Anchor server. */
    fun DirectoryListingDto.toDomain(): DirectoryListing = DirectoryListing(
        path = MediaPath(path),
        parentPath = parentPath?.let { MediaPath(it) },
        items = files.map { it.toDomain() },
        totalItems = totalFiles,
        totalSize = totalSize
    )

    // ── File → Domain (local source) ──────────────────────────

    fun File.toDomain(baseDir: File, alias: String): MediaItem =
        MediaItem.fromFile(this, baseDir, Alias(alias))

    fun filesToDirectoryListing(
        baseDir: File,
        alias: String,
        targetDir: File
    ): DirectoryListing {
        val files = targetDir.listFiles() ?: emptyArray()
        val visibleFiles = files.filterNot { it.name.startsWith(".") }

        val aliasObj = Alias(alias)
        val relativePath = if (targetDir == baseDir) ""
        else targetDir.relativeTo(baseDir).path.replace("\\", "/")

        val parentPath = when {
            relativePath.isEmpty() -> null
            relativePath.contains("/") -> {
                val parentRel = relativePath.substringBeforeLast("/")
                MediaPath.from(aliasObj, parentRel)
            }
            else -> MediaPath("/$alias")
        }

        val items = visibleFiles
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { it.toDomain(baseDir, alias) }

        val totalSize = visibleFiles.filter { it.isFile }.sumOf { it.length() }

        return DirectoryListing(
            path = MediaPath.from(aliasObj, relativePath),
            parentPath = parentPath,
            items = items,
            totalItems = items.size,
            totalSize = totalSize
        )
    }

    // ── Domain → DTO (server serialisation) ───────────────────

    fun MediaItem.toDto(): MediaFileDto = MediaFileDto(
        name = name,
        path = path.value,
        absolutePath = absolutePath,
        isDirectory = isDirectory,
        size = size,
        mimeType = mimeType.value,
        lastModified = lastModified,
        mediaType = mediaType.name
    )

    fun DirectoryListing.toDto(): DirectoryListingDto = DirectoryListingDto(
        path = path.value,
        parentPath = parentPath?.value,
        files = items.map { it.toDto() },
        totalFiles = totalItems,
        totalSize = totalSize
    )
}