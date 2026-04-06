package com.example.anchor.server.routing

import com.example.anchor.server.handler.FileHandler
import com.example.anchor.server.handler.ThumbnailHandler
import io.ktor.server.application.Application
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Registers file-serving, streaming, and thumbnail routes.
 */
fun Application.fileRoutes(
    fileHandler: FileHandler,
    thumbnailHandler: ThumbnailHandler
) {
    routing {
        get("/files/{alias}/{path...}") {
            fileHandler.handleServe(call)
        }
        get("/stream/{alias}/{path...}") {
            fileHandler.handleStream(call)
        }
        get("/thumbnail/{alias}/{path...}") {
            thumbnailHandler.handle(call)
        }
    }
}
