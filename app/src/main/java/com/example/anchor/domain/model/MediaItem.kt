package com.example.anchor.domain.model

import com.example.anchor.core.util.MimeTypeUtils
import java.io.File
import java.net.URLEncoder

/**
 * Domain model for a single file or directory in the shared media library.
 */
data class MediaItem(
    val name: String,
    val path: MediaPath,
    val absolutePath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: MimeType = MimeType(""),
    val lastModified: Long = 0L,
    val mediaType: MediaType = MediaType.UNKNOWN
) {

    companion object {
        fun fromFile(file: File, baseDir: File, alias: Alias): MediaItem {
            val relativePath = if (file == baseDir) ""
            else file.relativeTo(baseDir).path.replace("\\", "/")

            val mime = if (file.isDirectory) MimeType("") 
                      else MimeType.fromFileName(file.name)
            
            val type = if (file.isDirectory) MediaType.UNKNOWN
                      else MediaType.fromMimeType(mime.value)

            return MediaItem(
                name = file.name,
                path = MediaPath.from(alias, relativePath),
                absolutePath = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                mimeType = mime,
                lastModified = file.lastModified(),
                mediaType = type
            )
        }
    }

    // ── Derived helpers ───────────────────────────────────────

    /**
     * URL-safe percent-encoded path for building HTTP request URLs.
     */
    val encodedPath: String
        get() = path.value.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) ""
            else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    /**
     * Human-readable formatted file size.
     */
    val formattedSize: String
        get() = when {
            isDirectory -> ""
            size < 1_024L -> "$size B"
            size < 1_048_576L -> String.format("%.1f KB", size / 1_024.0)
            size < 1_073_741_824L -> String.format("%.1f MB", size / 1_048_576.0)
            else -> String.format("%.2f GB", size / 1_073_741_824.0)
        }

    val isStreamable: Boolean get() = mediaType.isStreamable
    val hasThumbnail: Boolean get() = mediaType.hasThumbnail
}

// ── MediaType ─────────────────────────────────────────────────

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    UNKNOWN;

    val isStreamable: Boolean get() = this == VIDEO || this == AUDIO
    val hasThumbnail: Boolean get() = this == VIDEO || this == IMAGE || this == AUDIO

    companion object {
        fun fromMimeType(mimeType: String): MediaType = when {
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("application/pdf") || mimeType.startsWith("text/") -> DOCUMENT
            else -> UNKNOWN
        }
    }
}

// ── DirectoryListing ──────────────────────────────────────────

data class DirectoryListing(
    val path: MediaPath,
    val parentPath: MediaPath?,
    val items: List<MediaItem>,
    val totalItems: Int,
    val totalSize: Long
)
