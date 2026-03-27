package com.example.anchor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of a file or directory returned by GET /api/browse/.
 *
 * Mirrors the JSON produced by [AnchorHttpServer]'s browse endpoint.
 * All optional fields use explicit defaults so older server versions that
 * omit them don't break deserialization.
 */
@Serializable
data class MediaFileDto(
    val name: String,
    val path: String,

    @SerialName("absolute_path")
    val absolutePath: String = "",

    @SerialName("is_directory")
    val isDirectory: Boolean = false,

    val size: Long = 0L,

    @SerialName("mime_type")
    val mimeType: String = "",

    @SerialName("last_modified")
    val lastModified: Long = 0L,

    @SerialName("media_type")
    val mediaType: String = "UNKNOWN"
)
