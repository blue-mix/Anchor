package com.example.anchor.domain.model

import java.net.URLEncoder

/**
 * Domain model for a single file or directory in the shared media library.
 *
 * This is the canonical in-memory representation used by ViewModels and
 * use-cases. It is deliberately free of Android framework and serialisation
 * dependencies — those live in the data layer.
 */
data class MediaItem(
    /** Display name (last path component). */
    val name: String,

    /**
     * Path relative to the shared root, prefixed with the alias.
     * Example: "/movies/Action/film.mp4"
     */
    val path: String,

    /** Absolute filesystem path — used by the server to serve the file. */
    val absolutePath: String,

    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: String = "",
    val lastModified: Long = 0L,
    val mediaType: MediaType = MediaType.UNKNOWN
) {

    // ── Derived helpers ───────────────────────────────────────

    /**
     * URL-safe percent-encoded path for building HTTP request URLs.
     * Each segment is encoded independently so slashes are preserved.
     */
    val encodedPath: String
        get() = path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    /**
     * Human-readable formatted file size (e.g. "4.5 MB").
     * Returns an empty string for directories.
     */
    val formattedSize: String
        get() = when {
            isDirectory -> ""
            size < 1_024L -> "$size B"
            size < 1_048_576L -> String.format("%.1f KB", size / 1_024.0)
            size < 1_073_741_824L -> String.format("%.1f MB", size / 1_048_576.0)
            else -> String.format("%.2f GB", size / 1_073_741_824.0)
        }

    /**
     * True when this item can be streamed by ExoPlayer (video or audio).
     */
    val isStreamable: Boolean
        get() = mediaType == MediaType.VIDEO || mediaType == MediaType.AUDIO

    /**
     * True when this item has a displayable thumbnail.
     */
    val hasThumbnail: Boolean
        get() = mediaType == MediaType.VIDEO ||
                mediaType == MediaType.IMAGE ||
                mediaType == MediaType.AUDIO
}

// ── MediaType ─────────────────────────────────────────────────

/**
 * High-level media classification for a file.
 * Kept in the domain layer because ViewModels make decisions based on it
 * (e.g. which icon to show, whether to offer a stream URL).
 */
enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    UNKNOWN;

    companion object {

        /** Infers the type from a MIME-type string. */
        fun fromMimeType(mimeType: String): MediaType = when {
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("application/pdf") -> DOCUMENT
            mimeType.startsWith("text/") -> DOCUMENT
            else -> UNKNOWN
        }

        /** Infers the type from a lowercase file extension. */
        fun fromExtension(extension: String): MediaType = when (extension.lowercase()) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv",
            "webm", "m4v", "3gp", "ts", "ogv", "divx" -> VIDEO

            "mp3", "wav", "flac", "aac", "ogg", "m4a",
            "wma", "opus", "aiff", "aif" -> AUDIO

            "jpg", "jpeg", "png", "gif", "webp",
            "bmp", "heic", "heif", "avif", "tiff" -> IMAGE

            "pdf", "txt", "doc", "docx",
            "xls", "xlsx", "csv", "md" -> DOCUMENT

            else -> UNKNOWN
        }
    }
}

// ── DirectoryListing ──────────────────────────────────────────

/**
 * Result of browsing a directory: its contents plus navigation context.
 */
data class DirectoryListing(
    /**
     * The canonical path of the browsed directory (e.g. "/movies/Action").
     */
    val path: String,

    /**
     * Path of the parent directory, or null when already at a shared root.
     */
    val parentPath: String?,

    /** All non-hidden items inside this directory, sorted directories first. */
    val items: List<MediaItem>,

    val totalItems: Int,
    val totalSize: Long
)