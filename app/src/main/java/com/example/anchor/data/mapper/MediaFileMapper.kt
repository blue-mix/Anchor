package com.example.anchor.data.mapper

import com.example.anchor.core.util.MimeTypeUtils
import com.example.anchor.data.dto.DirectoryListingDto
import com.example.anchor.data.dto.MediaFileDto
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.model.MediaType
import java.io.File
import java.net.URLEncoder

/**
 * Maps between [MediaFileDto] / [DirectoryListingDto] (data layer wire types)
 * and [MediaItem] / [DirectoryListing] (domain layer types).
 *
 * Also handles the common case of converting a [java.io.File] directly to
 * a domain [MediaItem] — used by the local file-system data source.
 */
object MediaFileMapper {

    // ── DTO → Domain ──────────────────────────────────────────

    /** Converts a [MediaFileDto] received from a remote Anchor server. */
    fun MediaFileDto.toDomain(): MediaItem = MediaItem(
        name = name,
        path = path,
        absolutePath = absolutePath,
        isDirectory = isDirectory,
        size = size,
        mimeType = mimeType,
        lastModified = lastModified,
        mediaType = MediaType.valueOf(mediaType.uppercase().let {
            // Guard against unknown values from older server versions
            if (MediaType.entries.any { e -> e.name == it }) it else "UNKNOWN"
        })
    )

    /** Converts a [DirectoryListingDto] received from a remote Anchor server. */
    fun DirectoryListingDto.toDomain(): DirectoryListing = DirectoryListing(
        path = path,
        parentPath = parentPath,
        items = files.map { it.toDomain() },
        totalItems = totalFiles,
        totalSize = totalSize
    )

    // ── File → Domain (local source) ──────────────────────────

    /**
     * Converts a local [File] to a domain [MediaItem].
     *
     * @param baseDir  The shared root containing this file — used to build
     *                 the relative [MediaItem.path].
     * @param alias    The URL alias of [baseDir] (e.g. "movies").
     */
    fun File.toDomain(baseDir: File, alias: String): MediaItem {
        val relative = if (this == baseDir) ""
        else relativeTo(baseDir).path.replace("\\", "/")

        val mimeType = if (isDirectory) "" else MimeTypeUtils.getMimeType(name)
        val mediaType = if (isDirectory) MediaType.UNKNOWN
        else MediaType.fromMimeType(mimeType)

        return MediaItem(
            name = name,
            path = buildPath(alias, relative),
            absolutePath = absolutePath,
            isDirectory = isDirectory,
            size = if (isFile) length() else 0L,
            mimeType = mimeType,
            lastModified = lastModified(),
            mediaType = mediaType
        )
    }

    /**
     * Converts a list of [File] entries inside [targetDir] to a [DirectoryListing].
     *
     * @param baseDir  Shared root directory.
     * @param alias    URL alias for [baseDir].
     * @param targetDir The specific subdirectory being listed.
     */
    fun filesToDirectoryListing(
        baseDir: File,
        alias: String,
        targetDir: File
    ): DirectoryListing {
        val relativePath = if (targetDir == baseDir) ""
        else targetDir.relativeTo(baseDir).path.replace("\\", "/")

        val parentPath = when {
            relativePath.isEmpty() -> null
            relativePath.contains("/") -> {
                val parentRel = relativePath.substringBeforeLast("/")
                buildPath(alias, parentRel)
            }

            else -> "/$alias"
        }

        val items = (targetDir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { it.toDomain(baseDir, alias) }

        return DirectoryListing(
            path = buildPath(alias, relativePath),
            parentPath = parentPath,
            items = items,
            totalItems = items.size,
            totalSize = items.filter { !it.isDirectory }.sumOf { it.size }
        )
    }

    // ── Domain → DTO (server serialisation) ───────────────────

    /**
     * Converts a domain [MediaItem] back to [MediaFileDto] for JSON
     * serialisation by the Anchor HTTP server.
     */
    fun MediaItem.toDto(): MediaFileDto = MediaFileDto(
        name = name,
        path = path,
        absolutePath = absolutePath,
        isDirectory = isDirectory,
        size = size,
        mimeType = mimeType,
        lastModified = lastModified,
        mediaType = mediaType.name
    )

    /** Converts a domain [DirectoryListing] to its DTO equivalent. */
    fun DirectoryListing.toDto(): DirectoryListingDto = DirectoryListingDto(
        path = path,
        parentPath = parentPath,
        files = items.map { it.toDto() },
        totalFiles = totalItems,
        totalSize = totalSize
    )

    // ── Internal helpers ──────────────────────────────────────

    /**
     * Builds a canonical path like "/movies/Action/film.mp4".
     * Handles the root case (empty relativePath → "/alias").
     */
    private fun buildPath(alias: String, relativePath: String): String =
        if (relativePath.isEmpty()) "/$alias"
        else "/$alias/$relativePath".replace("//", "/")

    /**
     * URL-encodes each segment of [path] for use in HTTP request URLs.
     */
    fun encodePath(path: String): String =
        path.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) ""
            else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
}