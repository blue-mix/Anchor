package com.example.anchor.server

import android.util.Log
import com.example.anchor.server.handler.BrowseHandler
import com.example.anchor.server.handler.FileHandler
import com.example.anchor.server.handler.SoapHandler
import com.example.anchor.server.handler.StreamingExceptionHandler
import com.example.anchor.server.handler.ThumbnailHandler
import com.example.anchor.server.routing.apiRoutes
import com.example.anchor.server.routing.dlnaRoutes
import com.example.anchor.server.routing.fileRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

data class RouteHandlers(
    val browse: BrowseHandler,
    val file: FileHandler,
    val thumbnail: ThumbnailHandler,
    val soap: SoapHandler
)

class RouteProvider(
    private val directoryManager: SharedDirectoryManager,
    private val handlers: RouteHandlers,
    private val dlnaManager: DlnaManager,
    private val serverInfoBuilder: ServerInfoBuilder,
    private val json: Json
) {
    companion object {
        private const val TAG = "RouteProvider"
    }

    fun configureRoutes(application: Application) {
        application.apply {
            installPlugins()

            directoryManager.getAll()

            apiRoutes(
                browseHandler = handlers.browse,
                buildServerInfo = serverInfoBuilder::build
            )
            fileRoutes(
                fileHandler = handlers.file,
                thumbnailHandler = handlers.thumbnail
            )
            dlnaRoutes(
                dlnaGenerator = dlnaManager.getGenerator(),
                soapHandler = handlers.soap
            )
        }
    }

    private fun Application.installPlugins() {
        install(ContentNegotiation) { json(json) }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Range)
            allowHeader(HttpHeaders.AcceptRanges)
            exposeHeader(HttpHeaders.ContentLength)
            exposeHeader(HttpHeaders.AcceptRanges)
            exposeHeader(HttpHeaders.ContentRange)
        }

        install(PartialContent) { maxRangeCount = 10 }
        install(AutoHeadResponse)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                if (StreamingExceptionHandler.isExpectedDisconnection(cause)) {
                    Log.d(TAG, StreamingExceptionHandler.getLogMessage(cause))
                    return@exception
                }

                Log.e(TAG, "Server error", cause)
                call.respondText(
                    "Internal server error: ${cause.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }

    }
}
