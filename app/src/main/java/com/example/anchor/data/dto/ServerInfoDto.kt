package com.example.anchor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of GET / or GET /api/info — server metadata.
 */
@Serializable
data class ServerInfoDto(
    val name: String = "",
    val version: String = "",

    @SerialName("device_name")
    val deviceName: String = "",

    @SerialName("shared_directories")
    val sharedDirectories: List<String> = emptyList(),

    @SerialName("total_files")
    val totalFiles: Int = 0,

    @SerialName("total_size")
    val totalSize: Long = 0L
)
