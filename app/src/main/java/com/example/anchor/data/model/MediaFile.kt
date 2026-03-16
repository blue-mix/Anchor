// app/src/main/java/com/example/anchor/server/MediaFile.kt

package com.example.anchor.data.model

import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * Represents a file or directory in the shared media library.
 */
@Serializable
data class MediaFile(
    val name: String,
    val path: String,                    // Relative path from shared root
    val absolutePath: String,            // Full filesystem path
    val isDirectory: Boolean,
    val size: Long = 0,
    val mimeType: String = "",
    val lastModified: Long = 0,
    val mediaType: MediaType = MediaType.UNKNOWN
) {
    /**
     * URL-safe encoded path for HTTP requests.
     */
    val encodedPath: String
        get() = path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8")
        }

    /**
     * Human-readable file size.
     */
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
}

/**
 * Type of media file.
 */
@Serializable
enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    UNKNOWN;

    companion object {
        fun fromMimeType(mimeType: String): MediaType {
            return when {
                mimeType.startsWith("video/") -> VIDEO
                mimeType.startsWith("audio/") -> AUDIO
                mimeType.startsWith("image/") -> IMAGE
                mimeType.startsWith("application/pdf") -> DOCUMENT
                mimeType.startsWith("text/") -> DOCUMENT
                else -> UNKNOWN
            }
        }

        fun fromExtension(extension: String): MediaType {
            return when (extension.lowercase()) {
                // Video
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp" -> VIDEO
                // Audio
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus" -> AUDIO
                // Image
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> IMAGE
                // Document
                "pdf", "txt", "doc", "docx", "xls", "xlsx" -> DOCUMENT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Directory listing response.
 */
@Serializable
data class DirectoryListing(
    val path: String,
    val parentPath: String?,
    val files: List<MediaFile>,
    val totalFiles: Int,
    val totalSize: Long
)

/**
 * Server information response.
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String,
    val deviceName: String,
    val sharedDirectories: List<String>,
    val totalFiles: Int,
    val totalSize: Long
)