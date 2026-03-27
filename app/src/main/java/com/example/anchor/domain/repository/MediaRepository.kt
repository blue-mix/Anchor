package com.example.anchor.domain.repository

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import java.io.File

/**
 * Contract for all local filesystem media operations.
 *
 * Implementations live in the data layer ([MediaRepositoryImpl]).
 * Server handlers and ViewModels depend only on this interface.
 */
interface MediaRepository {

    /**
     * Lists the contents of [baseDir]/[relativePath].
     *
     * @param baseDir      The shared root directory (alias → File mapping).
     * @param relativePath Path relative to [baseDir]; empty string = root.
     * @return [Result.Success] with a [DirectoryListing], or
     *         [Result.Error] when the path is missing, is not a directory,
     *         or would escape [baseDir] (path-traversal attempt).
     */
    suspend fun browse(baseDir: File, relativePath: String): Result<DirectoryListing>

    /**
     * Returns the [MediaItem] metadata for a single file at
     * [baseDir]/[relativePath].
     *
     * @return [Result.Error] when the file is missing or outside [baseDir].
     */
    suspend fun getFile(baseDir: File, relativePath: String): Result<MediaItem>

    /**
     * Generates or retrieves a cached JPEG thumbnail for [file].
     *
     * Supports video (frame at 10 %), image (resized), and audio (album art).
     *
     * @return [Result.Success] with raw JPEG bytes, or
     *         [Result.Error] when no thumbnail can be generated.
     */
    suspend fun getThumbnail(file: File): Result<ByteArray>

    /**
     * Recursively counts files and total bytes under [directory].
     * Used by the dashboard to show "N files, X MB" per shared folder.
     */
    suspend fun getDirectoryStats(directory: File): Result<DirectoryStats>

    data class DirectoryStats(
        val fileCount: Int,
        val totalSize: Long
    )
}