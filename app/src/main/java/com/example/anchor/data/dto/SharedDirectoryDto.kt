package com.example.anchor.data.dto

import kotlinx.serialization.Serializable


/**
 * Wire representation of GET /api/directories — list of shared roots.
 */
@Serializable
data class SharedDirectoryDto(
    val alias: String,
    val name: String,
    val path: String
)