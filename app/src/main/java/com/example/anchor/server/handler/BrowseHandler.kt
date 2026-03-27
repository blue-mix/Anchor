package com.example.anchor.server.handler

import com.example.anchor.data.mapper.MediaFileMapper.toDto
import com.example.anchor.domain.repository.MediaRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.io.File
import java.net.URLDecoder

/**
 * Handles /api/directories and /api/browse/ route logic.
 *
 * Changes from Phase 2:
 *  - Imports [MediaRepository] from domain.repository (not data.repository).
 *  - Converts [DirectoryListing] to [DirectoryListingDto] via
 *    [MediaFileMapper.toDto] before responding, so Ktor serialises the
 *    DTO (which has @Serializable) rather than the domain type (which does not).
 *  - Uses [Result.onSuccess] / [Result.onError] callbacks consistently.
 *  - [listing.items] replaces [listing.files].
 */
class BrowseHandler(private val mediaRepository: MediaRepository) {

    suspend fun handleListDirectories(
        call: ApplicationCall,
        sharedDirectories: Map<String, File>
    ) {
        val list = sharedDirectories.map { (alias, dir) ->
            mapOf(
                "alias" to alias,
                "name" to dir.name,
                "path" to "/$alias"
            )
        }
        call.respond(list)
    }

    suspend fun handleBrowse(
        call: ApplicationCall,
        sharedDirectories: Map<String, File>
    ) {
        val alias = call.parameters["alias"]
            ?: run { call.respond(HttpStatusCode.BadRequest, "Missing alias"); return }

        val relativePath = call.parameters.getAll("path")
            ?.joinToString("/")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: ""

        val baseDir = sharedDirectories[alias]
            ?: run { call.respond(HttpStatusCode.NotFound, "Unknown alias: $alias"); return }

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
}