package com.example.anchor.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.data.dto.ServerInfoDto
import com.example.anchor.domain.repository.MediaRepository
import com.example.anchor.server.dlna.ContentDirectoryService
import com.example.anchor.server.dlna.DlnaDescriptionGenerator
import com.example.anchor.server.dlna.SsdpAnnouncer
import com.example.anchor.server.handler.BrowseHandler
import com.example.anchor.server.handler.FileHandler
import com.example.anchor.server.handler.SoapHandler
import com.example.anchor.server.handler.ThumbnailHandler
import com.example.anchor.server.routing.apiRoutes
import com.example.anchor.server.routing.dlnaRoutes
import com.example.anchor.server.routing.fileRoutes
import com.example.anchor.server.service.AnchorServiceState
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import java.io.File

class AnchorHttpServer(
    private val context: Context,
    private val port: Int = 8080,
    private val mediaRepository: MediaRepository,
    private val json: Json
) {
    companion object {
        private const val TAG = "AnchorHttpServer"
    }

    // ── Shared directory map ──────────────────────────────────

    private val sharedDirectories = mutableMapOf<String, File>()

    fun addSharedDirectory(alias: String, directory: File) {
        if (directory.exists() && directory.isDirectory) {
            sharedDirectories[alias] = directory
            Log.d(TAG, "Shared: $alias → ${directory.absolutePath}")
            syncContentDirectory()
        } else {
            Log.w(TAG, "Invalid directory: ${directory.absolutePath}")
        }
    }

    fun removeSharedDirectory(alias: String) {
        sharedDirectories.remove(alias)
        syncContentDirectory()
    }

    fun clearSharedDirectories() {
        sharedDirectories.clear()
        syncContentDirectory()
    }

    // ── DLNA / UPnP components ────────────────────────────────

    private var dlnaGenerator: DlnaDescriptionGenerator? = null
    private var contentDirectory: ContentDirectoryService? = null
    private var ssdpAnnouncer: SsdpAnnouncer? = null

    // ── Route handlers ────────────────────────────────────────

    private val fileHandler = FileHandler()
    private val browseHandler by lazy { BrowseHandler(mediaRepository) }
    private val thumbnailHandler by lazy { ThumbnailHandler(mediaRepository) }
    private var soapHandler: SoapHandler? = null

    // ── Ktor engine ───────────────────────────────────────────

    private var server: EmbeddedServer<NettyApplicationEngine,
            NettyApplicationEngine.Configuration>? = null

    // ── Lifecycle ─────────────────────────────────────────────

    fun start() {
        if (server != null) {
            Log.w(TAG, "Server already running"); return
        }

        val deviceUuid = context
            .getSharedPreferences("anchor_device", Context.MODE_PRIVATE)
            .getString("uuid", null)
            ?: java.util.UUID.randomUUID().toString().also {
                context.getSharedPreferences("anchor_device", Context.MODE_PRIVATE)
                    .edit().putString("uuid", it).apply()
            }

        val baseUrl = NetworkUtils.getLocalIpAddress(context)
            ?.let { "http://$it:$port" } ?: ""

        dlnaGenerator = DlnaDescriptionGenerator(context, deviceUuid, port)
        contentDirectory = ContentDirectoryService(baseUrl)
            .also { it.updateSharedDirectories(sharedDirectories.toMap()) }
        soapHandler = SoapHandler(dlnaGenerator!!, contentDirectory!!)

        server = embeddedServer(Netty, port = port) { configureRoutes() }
            .start(wait = false)

        ssdpAnnouncer = SsdpAnnouncer(context, port).apply { start() }

        Log.d(TAG, "Server started on port $port")
        AnchorServiceState.addLog("HTTP server started on port $port")
    }

    fun stop() {
        ssdpAnnouncer?.stop(); ssdpAnnouncer = null
        server?.stop(1000, 2000); server = null
        dlnaGenerator = null
        contentDirectory = null
        soapHandler = null

        Log.d(TAG, "Server stopped")
        AnchorServiceState.addLog("HTTP server stopped")
    }

    fun isRunning(): Boolean = server != null

    // ── Routing ───────────────────────────────────────────────

    private fun Application.configureRoutes() {
        installPlugins()

        // Snapshot the directory map so routing closures capture a stable copy
        val dirs = sharedDirectories.toMap()

        apiRoutes(
            sharedDirectories = dirs,
            browseHandler = browseHandler,
            buildServerInfo = ::buildServerInfo
        )
        fileRoutes(
            sharedDirectories = dirs,
            fileHandler = fileHandler,
            thumbnailHandler = thumbnailHandler
        )
        dlnaRoutes(
            dlnaGenerator = dlnaGenerator,
            soapHandler = soapHandler
        )
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
                Log.e(TAG, "Server error", cause)
                call.respondText(
                    "Internal server error: ${cause.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun syncContentDirectory() {
        contentDirectory?.updateSharedDirectories(sharedDirectories.toMap())
    }

    private fun buildServerInfo(): ServerInfoDto {
        var totalFiles = 0
        var totalSize = 0L
        sharedDirectories.values.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach {
                totalFiles++; totalSize += it.length()
            }
        }
        return ServerInfoDto(
            name = "Anchor Media Server",
            version = "1.0.0",
            deviceName = Build.MODEL,
            sharedDirectories = sharedDirectories.keys.toList(),
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }
}