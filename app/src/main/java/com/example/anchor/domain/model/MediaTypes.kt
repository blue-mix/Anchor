package com.example.anchor.domain.model

import com.example.anchor.core.util.MimeTypeUtils
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Alias(val value: String) {
    init {
        require(value.isNotEmpty()) { "Alias cannot be empty" }
    }
}

@Serializable
@JvmInline
value class MediaPath(val value: String) {
    init {
        require(!value.contains("..")) {
            "Path cannot contain directory traversal"
        }
    }

    fun segments(): List<String> = value.split("/").filter { it.isNotEmpty() }

    companion object {
        fun from(alias: Alias, relativePath: String): MediaPath {
            val normalizedRel = relativePath.trimStart('/').replace("//", "/")
            return MediaPath("/${alias.value}/$normalizedRel".trimEnd('/'))
        }
    }
}

@Serializable
@JvmInline
value class MimeType(val value: String) {
    val isVideo: Boolean get() = value.startsWith("video/")
    val isAudio: Boolean get() = value.startsWith("audio/")
    val isImage: Boolean get() = value.startsWith("image/")
    val isStreamable: Boolean get() = isVideo || isAudio

    companion object {
        fun fromFileName(fileName: String): MimeType {
            return MimeType(MimeTypeUtils.getMimeType(fileName))
        }
    }
}
