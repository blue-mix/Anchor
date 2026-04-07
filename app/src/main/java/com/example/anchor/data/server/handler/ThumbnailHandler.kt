package com.example.anchor.data.server.handler

import com.example.anchor.core.extension.isDescendantOf
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.data.server.PathResolver
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import java.io.File

/**
 * Handles /thumbnail/ route logic.
 */
class ThumbnailHandler(
    private val mediaRepository: MediaRepository,
    private val pathResolver: PathResolver
) {

    suspend fun handle(call: ApplicationCall) {
        pathResolver.resolve(call)
            .onSuccess { (_, relativePath, baseDir) ->
                val file = File(baseDir, relativePath)

                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound)
                    return@onSuccess
                }

                if (!file.isDescendantOf(baseDir)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@onSuccess
                }

                mediaRepository.getThumbnail(file)
                    .onSuccess { bytes -> call.respondBytes(bytes, ContentType.Image.JPEG) }
                    .onError { _, _ -> call.respond(HttpStatusCode.NotFound, "No thumbnail available") }
            }
            .onError { message, _ ->
                call.respond(HttpStatusCode.BadRequest, message)
            }
    }
}
