package com.example.anchor.server.routing

import com.example.anchor.server.handler.FileHandler
import com.example.anchor.server.handler.ThumbnailHandler
import io.ktor.server.application.Application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

/**
 * Registers file-serving, streaming, and thumbnail routes.
 *
 * GET /files/{alias}/{path...}     → direct download (with Accept-Ranges)
 * GET /stream/{alias}/{path...}    → range-aware streaming
 * GET /thumbnail/{alias}/{path...} → JPEG thumbnail
 */
fun Application.fileRoutes(
    sharedDirectories: Map<String, File>,
    fileHandler: FileHandler,
    thumbnailHandler: ThumbnailHandler
) {
    routing {
        get("/files/{alias}/{path...}") {
            fileHandler.handleServe(call, sharedDirectories)
        }
        get("/stream/{alias}/{path...}") {
            fileHandler.handleStream(call, sharedDirectories)
        }
        get("/thumbnail/{alias}/{path...}") {
            thumbnailHandler.handle(call, sharedDirectories)
        }
    }
}