package com.example.anchor.data.source.local

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
 *
 * Responsibilities:
 *  - List directory contents with security (path-traversal) checks
 *  - Return [MediaItem] metadata for individual files
 *  - Compute directory statistics (file count, total size)
 *
 * All operations are dispatched on [Dispatchers.IO].
 * This class has zero Android framework dependencies and is easily testable
 * with a [TempDir].
 */
class FileSystemDataSource {

    companion object {
        private const val TAG = "FileSystemDataSource"
    }

    // ── Browse ────────────────────────────────────────────────

    /**
     * Lists the contents of [baseDir]/[relativePath].
     *
     * Returns [Result.Error] when:
     *  - The resolved path does not exist.
     *  - The resolved path is not a directory.
     *  - The resolved path escapes [baseDir] (path-traversal attempt).
     */
    suspend fun browse(
        baseDir: File,
        alias: String,
        relativePath: String
    ): Result<DirectoryListing> = withContext(Dispatchers.IO) {
        resultOf {
            val targetDir = resolveAndValidate(baseDir, relativePath)
            require(targetDir.isDirectory) {
                "Path is not a directory: $relativePath"
            }
            filesToDirectoryListing(baseDir, alias, targetDir)
        }
    }

    // ── Single file ───────────────────────────────────────────

    /**
     * Returns [MediaItem] metadata for the file at [baseDir]/[relativePath].
     */
    suspend fun getFile(
        baseDir: File,
        alias: String,
        relativePath: String
    ): Result<MediaItem> = withContext(Dispatchers.IO) {
        resultOf {
            val file = resolveAndValidate(baseDir, relativePath)
            file.toDomain(baseDir, alias)
        }
    }

    /**
     * Resolves [relativePath] against [baseDir] and returns the raw [File]
     * without mapping — used by the server to stream bytes.
     *
     * Returns [Result.Error] on traversal or missing path.
     */
    suspend fun resolveFile(
        baseDir: File,
        relativePath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        resultOf { resolveAndValidate(baseDir, relativePath) }
    }

    // ── Stats ─────────────────────────────────────────────────

    /**
     * Counts regular files and their total size under [directory] recursively.
     */
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
     * Resolves [relativePath] against [baseDir] and validates:
     *  1. The resulting path exists.
     *  2. The canonical path starts with [baseDir]'s canonical path
     *     (prevents directory traversal attacks such as "../../etc/passwd").
     *
     * @throws IllegalArgumentException on missing path or traversal attempt.
     */
    internal fun resolveAndValidate(baseDir: File, relativePath: String): File {
        val target = if (relativePath.isEmpty()) baseDir
        else File(baseDir, relativePath)

        val canonicalTarget = target.canonicalPath
        val canonicalBase = baseDir.canonicalPath

        require(
            canonicalTarget == canonicalBase ||
                    canonicalTarget.startsWith(canonicalBase + File.separator)
        ) {
            "Access denied: path escapes the shared directory"
        }

        require(target.exists()) {
            "Path not found: $relativePath"
        }

        return target
    }
}