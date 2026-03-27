package com.example.anchor.server.routing

import com.example.anchor.server.dlna.DlnaDescriptionGenerator
import com.example.anchor.server.handler.SoapHandler
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/**
 * Registers all DLNA / UPnP routes.
 *
 * GET  /dlna/device.xml                    → root device description
 * GET  /dlna/ContentDirectory.xml          → ContentDirectory SCPD
 * GET  /dlna/ConnectionManager.xml         → ConnectionManager SCPD
 * POST /dlna/control/ContentDirectory      → ContentDirectory SOAP
 * POST /dlna/control/ConnectionManager     → ConnectionManager SOAP
 * *    /dlna/event/{service}               → stub (200 OK)
 * GET  /dlna/icon-{size}.png               → 404 (no bundled icons yet)
 */
fun Application.dlnaRoutes(
    dlnaGenerator: DlnaDescriptionGenerator?,
    soapHandler: SoapHandler?
) {
    routing {
        get("/dlna/device.xml") {
            call.respondText(
                dlnaGenerator?.getDeviceDescription() ?: "",
                ContentType.Text.Xml
            )
        }
        get("/dlna/ContentDirectory.xml") {
            call.respondText(
                dlnaGenerator?.getContentDirectoryDescription() ?: "",
                ContentType.Text.Xml
            )
        }
        get("/dlna/ConnectionManager.xml") {
            call.respondText(
                dlnaGenerator?.getConnectionManagerDescription() ?: "",
                ContentType.Text.Xml
            )
        }
        post("/dlna/control/ContentDirectory") {
            soapHandler?.handleContentDirectory(call)
                ?: call.respond(HttpStatusCode.ServiceUnavailable)
        }
        post("/dlna/control/ConnectionManager") {
            soapHandler?.handleConnectionManager(call)
                ?: call.respond(HttpStatusCode.ServiceUnavailable)
        }
        route("/dlna/event/{service}") {
            handle { call.respond(HttpStatusCode.OK) }
        }
        get("/dlna/icon-{size}.png") {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}