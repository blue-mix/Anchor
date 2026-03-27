package com.example.anchor.server.handler

import com.example.anchor.domain.repository.MediaRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import java.io.File
import java.net.URLDecoder

/**
 * Handles /thumbnail/ route logic.
 *
 * Changes from Phase 2:
 *  - Imports [MediaRepository] from domain.repository.
 *  - Canonical-path traversal check uses the same convention as
 *    [FileHandler] (startsWith + File.separator).
 *  - Uses [Result.onSuccess] / [Result.onError] callbacks.
 */
class ThumbnailHandler(private val mediaRepository: MediaRepository) {

    suspend fun handle(call: ApplicationCall, sharedDirectories: Map<String, File>) {
        val alias = call.parameters["alias"]
            ?: run { call.respond(HttpStatusCode.BadRequest); return }

        val relativePath = call.parameters.getAll("path")
            ?.joinToString("/")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: run { call.respond(HttpStatusCode.BadRequest); return }

        val baseDir = sharedDirectories[alias]
            ?: run { call.respond(HttpStatusCode.NotFound); return }

        val file = File(baseDir, relativePath)

        if (!file.exists() || file.isDirectory) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val canonical = file.canonicalPath
        val base = baseDir.canonicalPath
        if (canonical != base && !canonical.startsWith(base + File.separator)) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        mediaRepository.getThumbnail(file)
            .onSuccess { bytes -> call.respondBytes(bytes, ContentType.Image.JPEG) }
            .onError { _, _ -> call.respond(HttpStatusCode.NotFound, "No thumbnail available") }
    }
}