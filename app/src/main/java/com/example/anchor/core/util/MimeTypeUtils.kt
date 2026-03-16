// app/src/main/java/com/example/anchor/utils/MimeTypeUtils.kt

package com.example.anchor.core.util

import android.webkit.MimeTypeMap

/**
 * Utility for determining MIME types of files.
 */
object MimeTypeUtils {

    private val extensionToMimeType = mapOf(
        // Video
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "webm" to "video/webm",
        "m4v" to "video/x-m4v",
        "3gp" to "video/3gpp",
        "ts" to "video/mp2t",

        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "wma" to "audio/x-ms-wma",
        "opus" to "audio/opus",

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

        // Document
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "html" to "text/html",
        "json" to "application/json",
        "xml" to "application/xml",

        // Archive
        "zip" to "application/zip",
        "rar" to "application/x-rar-compressed",
        "7z" to "application/x-7z-compressed"
    )

    /**
     * Gets the MIME type for a file based on its extension.
     */
    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // First check our custom map
        extensionToMimeType[extension]?.let { return it }

        // Fall back to Android's MimeTypeMap
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }

        // Default to binary stream
        return "application/octet-stream"
    }

    /**
     * Gets the file extension for a MIME type.
     */
    fun getExtensionFromMimeType(mimeType: String): String? {
        // Check our custom map first
        extensionToMimeType.entries.find { it.value == mimeType }?.let { return it.key }

        // Fall back to Android's MimeTypeMap
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }

    /**
     * Checks if the MIME type is a video type.
     */
    fun isVideo(mimeType: String): Boolean = mimeType.startsWith("video/")

    /**
     * Checks if the MIME type is an audio type.
     */
    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")

    /**
     * Checks if the MIME type is an image type.
     */
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    /**
     * Checks if the file is streamable media.
     */
    fun isStreamable(mimeType: String): Boolean = isVideo(mimeType) || isAudio(mimeType)
}