package com.example.anchor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of GET /api/browse/directory response.
 */
@Serializable
data class DirectoryListingDto(
    val path: String,

    @SerialName("parent_path")
    val parentPath: String? = null,

    val files: List<MediaFileDto> = emptyList(),

    @SerialName("total_files")
    val totalFiles: Int = 0,

    @SerialName("total_size")
    val totalSize: Long = 0L
)
