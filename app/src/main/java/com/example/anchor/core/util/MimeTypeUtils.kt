package com.example.anchor.core.util

import android.webkit.MimeTypeMap

/**
 * Utility for determining MIME types from file names and extensions.
 * Maintains a hand-curated map of media extensions and falls back to
 * Android's [MimeTypeMap] for everything else.
 */
object MimeTypeUtils {

    // ── Extension → MIME map ──────────────────────────────────

    private val extensionToMimeType: Map<String, String> = mapOf(
        // Video - Updated to standard types for better HW decoder compatibility
        "mp4" to "video/mp4",
        "mkv" to "video/matroska", // Changed from x-matroska to matroska (official)
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "webm" to "video/webm",
        "m4v" to "video/x-m4v",
        "3gp" to "video/3gpp",
        "ts" to "video/mp2t",
        "mts" to "video/mp2t",
        "m2ts" to "video/mp2t",
        "ogv" to "video/ogg",
        "divx" to "video/x-msvideo",

        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "m4a" to "audio/mp4",
        "wma" to "audio/x-ms-wma",
        "opus" to "audio/opus",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
        "dsf" to "audio/dsf",
        "dsd" to "audio/dsd",

        // Image
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "svg" to "image/svg+xml",
        "tiff" to "image/tiff",
        "tif" to "image/tiff",
        "avif" to "image/avif",
        "ico" to "image/x-icon",

        // Document
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "html" to "text/html",
        "htm" to "text/html",
        "json" to "application/json",
        "xml" to "application/xml",
        "csv" to "text/csv",
        "md" to "text/markdown",

        // Archive
        "zip" to "application/zip",
        "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip"
    )

    // Reverse map for extension lookup
    private val mimeTypeToExtension: Map<String, String> by lazy {
        extensionToMimeType.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, keys) -> keys.first() }
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Returns the MIME type for [fileName] based on its extension.
     * Resolution order: custom map → Android MimeTypeMap → octet-stream.
     */
    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return "application/octet-stream"

        extensionToMimeType[extension]?.let { return it }

        // MimeTypeMap.getSingleton() can return null in unit tests even with isReturnDefaultValues
        try {
            MimeTypeMap.getSingleton()
                ?.getMimeTypeFromExtension(extension)
                ?.let { return it }
        } catch (e: Exception) {
            // Ignore failures in unit tests where MimeTypeMap is not available
        }

        return "application/octet-stream"
    }

    /**
     * Returns the preferred file extension for [mimeType], or null.
     */
    fun getExtensionFromMimeType(mimeType: String): String? {
        mimeTypeToExtension[mimeType]?.let { return it }

        return try {
            MimeTypeMap.getSingleton()?.getExtensionFromMimeType(mimeType)
        } catch (e: Exception) {
            null
        }
    }

    // ── Type checks ───────────────────────────────────────────

    /** Returns true when [mimeType] is a video type. */
    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    /** Returns true when [mimeType] is an audio type. */
    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    /** Returns true when [mimeType] is an image type. */
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    /** Returns true when [mimeType] is a document type (PDF, text, etc.). */
    fun isDocument(mimeType: String): Boolean =
        mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("text/")

    /**
     * Returns true when the file can be streamed — i.e., video or audio.
     * Used to decide whether to show a stream URL vs a plain download URL.
     */
    fun isStreamable(mimeType: String): Boolean = isVideo(mimeType) || isAudio(mimeType)

    /**
     * Returns true when [mimeType] indicates a media file of any kind
     * (video, audio, or image).
     */
    fun isMedia(mimeType: String): Boolean =
        isVideo(mimeType) || isAudio(mimeType) || isImage(mimeType)

    /**
     * Returns a short human-readable label for [mimeType].
     * Examples: "video/mp4" → "MP4", "audio/flac" → "FLAC".
     */
    fun getLabel(mimeType: String): String {
        val ext = getExtensionFromMimeType(mimeType)
        if (ext != null) return ext.uppercase()

        // Fallback: strip the subtype prefix
        return mimeType.substringAfterLast('/').substringAfterLast('-').uppercase()
    }
}