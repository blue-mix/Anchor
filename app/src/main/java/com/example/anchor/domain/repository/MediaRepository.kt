package com.example.anchor.domain.repository

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import java.io.File

/**
 * Contract for all local filesystem media operations.
 */
interface MediaRepository {

    /**
     * Lists the contents of [baseDir]/[relativePath].
     */
    suspend fun browse(baseDir: File, relativePath: String): Result<DirectoryListing>

    /**
     * Returns the [MediaItem] metadata for a single file at
     * [baseDir]/[relativePath].
     */
    suspend fun getFile(baseDir: File, relativePath: String): Result<MediaItem>

    /**
     * Generates or retrieves a cached JPEG thumbnail for [file].
     */
    suspend fun getThumbnail(file: File): Result<ByteArray>

    /**
     * Recursively counts files and total bytes under [directory].
     */
    suspend fun getDirectoryStats(directory: File): Result<DirectoryStats>

    data class DirectoryStats(
        val fileCount: Int,
        val totalSize: Long
    )
}