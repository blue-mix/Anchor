package com.example.anchor.data.server.handler

import com.example.anchor.data.mapper.MediaFileMapper.toDto
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.data.server.PathResolver
import com.example.anchor.data.server.SharedDirectoryManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.io.File

/**
 * Handles /api/directories and /api/browse/ route logic.
 */
class BrowseHandler(
    private val mediaRepository: MediaRepository,
    private val pathResolver: PathResolver,
    private val directoryManager: SharedDirectoryManager
) {

    suspend fun handleListDirectories(call: ApplicationCall) {
        val list = directoryManager.getAll().map { (alias, dir) ->
            mapOf(
                "alias" to alias,
                "name" to dir.name,
                "path" to "/$alias"
            )
        }
        call.respond(list)
    }

    suspend fun handleBrowse(call: ApplicationCall) {
        pathResolver.resolve(call)
            .onSuccess { (alias, relativePath, baseDir) ->
                // If relativePath points at a file (not a directory), return its metadata
                if (relativePath.isNotEmpty()) {
                    val target = File(baseDir, relativePath)
                    if (target.exists() && !target.isDirectory) {
                        mediaRepository.getFile(baseDir, relativePath)
                            .onSuccess { item -> call.respond(item.toDto()) }
                            .onError { msg, _ -> call.respond(HttpStatusCode.NotFound, msg) }
                        return
                    }
                }

                // Otherwise return a full directory listing
                mediaRepository.browse(baseDir, relativePath)
                    .onSuccess { listing -> call.respond(listing.toDto()) }
                    .onError { msg, _ -> call.respond(HttpStatusCode.NotFound, msg) }
            }
            .onError { message, _ ->
                call.respond(HttpStatusCode.BadRequest, message)
            }
    }
}
