package com.example.anchor.server.routing

import com.example.anchor.data.dto.ServerInfoDto
import com.example.anchor.server.handler.BrowseHandler
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

/**
 * Registers info and directory-listing API routes.
 *
 * GET /             → server metadata
 * GET /api/info     → same as /
 * GET /api/directories → list of shared alias → name → path
 * GET /api/browse/ → directory / file listing via [BrowseHandler]
 */
fun Application.apiRoutes(
    sharedDirectories: Map<String, File>,
    browseHandler: BrowseHandler,
    buildServerInfo: () -> ServerInfoDto
) {
    routing {
        get("/") {
            call.respond(buildServerInfo())
        }
        get("/api/info") {
            call.respond(buildServerInfo())
        }
        get("/api/directories") {
            browseHandler.handleListDirectories(call, sharedDirectories)
        }
        get("/api/browse/{alias}/{path...}") {
            browseHandler.handleBrowse(call, sharedDirectories)
        }
    }
}