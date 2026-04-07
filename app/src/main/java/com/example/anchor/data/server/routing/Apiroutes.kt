package com.example.anchor.data.server.routing

import com.example.anchor.data.dto.ServerInfoDto
import com.example.anchor.data.server.handler.BrowseHandler
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Registers info and directory-listing API routes.
 */
fun Application.apiRoutes(
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
            browseHandler.handleListDirectories(call)
        }
        get("/api/browse/{alias}/{path...}") {
            browseHandler.handleBrowse(call)
        }
    }
}
