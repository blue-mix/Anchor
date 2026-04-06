package com.example.anchor.data.source.local

import com.example.anchor.core.extension.isDescendantOf
import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf
import com.example.anchor.data.mapper.MediaFileMapper.filesToDirectoryListing
import com.example.anchor.data.mapper.MediaFileMapper.toDomain
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.repository.MediaRepository.DirectoryStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles all direct filesystem access for the local media library.
 */
class FileSystemDataSource {

    companion object {
        private const val TAG = "FileSystemDataSource"
    }

    // ── Browse ────────────────────────────────────────────────

    suspend fun browse(
        baseDir: File,
        alias: String,
        relativePath: String
    ): Result<DirectoryListing> = withContext(Dispatchers.IO) {
        resolveAndValidate(baseDir, relativePath).flatMap { targetDir ->
            resultOf {
                require(targetDir.isDirectory) {
                    "Path is not a directory: $relativePath"
                }
                filesToDirectoryListing(baseDir, alias, targetDir)
            }
        }
    }

    // ── Single file ───────────────────────────────────────────

    suspend fun getFile(
        baseDir: File,
        alias: String,
        relativePath: String
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        resolveAndValidate(baseDir, relativePath).map { file ->
            file.toDomain(baseDir, alias)
        }
    }

    suspend fun resolveFile(
        baseDir: File,
        relativePath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        resolveAndValidate(baseDir, relativePath)
    }

    // ── Stats ─────────────────────────────────────────────────

    suspend fun getDirectoryStats(directory: File): Result<DirectoryStats> =
        withContext(Dispatchers.IO) {
            resultOf {
                var fileCount = 0
                var totalSize = 0L
                directory.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        fileCount++
                        totalSize += file.length()
                    }
                DirectoryStats(fileCount, totalSize)
            }
        }

    // ── Path validation ───────────────────────────────────────

    /**
     * Resolves [relativePath] against [baseDir] and validates security.
     * Returns [Result<File>].
     */
    internal fun resolveAndValidate(baseDir: File, relativePath: String): Result<File> = resultOf {
        require(!relativePath.contains("..")) {
            "Path contains directory traversal sequence"
        }

        val target = if (relativePath.isEmpty()) baseDir
        else File(baseDir, relativePath)

        // Robust path traversal check using extension function
        require(target.isDescendantOf(baseDir)) {
            "Access denied: path escapes the shared directory"
        }

        require(target.exists()) {
            "Path not found: $relativePath"
        }

        target
    }
}
