package com.example.anchor.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.anchor.core.util.MimeTypeUtils
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.data.model.DirectoryListing
import com.example.anchor.data.model.MediaFile
import com.example.anchor.data.model.MediaType
import com.example.anchor.data.model.ServerInfo
import com.example.anchor.data.model.ThumbnailGenerator
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder

/**
 * Ktor-based HTTP server for serving media files.
 */
class AnchorHttpServer(
    private val context: Context,
    private val port: Int = 8080
) {
    companion object {
        private const val TAG = "AnchorHttpServer"
        private const val BUFFER_SIZE = 64 * 1024  // 64KB buffer for streaming
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? =
        null
    private val thumbnailGenerator = ThumbnailGenerator(context)

    // Shared directories mapped by alias
    private val sharedDirectories = mutableMapOf<String, File>()
    private var dlnaHandler: DlnaDescriptionHandler? = null
    private var contentDirectory: ContentDirectoryService? = null
    private var ssdpAnnouncer: SsdpAnnouncer? = null


    // Add this method to sync directories with ContentDirectory
    private fun syncContentDirectory() {
        val baseUrl = NetworkUtils.getLocalIpAddress(context)?.let { "http://$it:$port" } ?: ""

        if (contentDirectory == null) {
            contentDirectory = ContentDirectoryService(baseUrl)
        }

        contentDirectory?.updateSharedDirectories(sharedDirectories.toMap())
    }

    // Update addSharedDirectory to sync
    fun addSharedDirectory(alias: String, directory: File) {
        if (directory.exists() && directory.isDirectory) {
            sharedDirectories[alias] = directory
            Log.d(TAG, "Added shared directory: $alias -> ${directory.absolutePath}")
            syncContentDirectory()  // Add this line
        } else {
            Log.w(TAG, "Invalid directory: ${directory.absolutePath}")
        }
    }

    // Update removeSharedDirectory to sync
    fun removeSharedDirectory(alias: String) {
        sharedDirectories.remove(alias)
        syncContentDirectory()  // Add this line
    }
//    /**
//     * Adds a directory to be shared.
//     * @param alias The URL path alias (e.g., "videos" -> /browse/videos/...)
//     * @param directory The actual filesystem directory
//     */
//    fun addSharedDirectory(alias: String, directory: File) {
//        if (directory.exists() && directory.isDirectory) {
//            sharedDirectories[alias] = directory
//            Log.d(TAG, "Added shared directory: $alias -> ${directory.absolutePath}")
//        } else {
//            Log.w(TAG, "Invalid directory: ${directory.absolutePath}")
//        }
//    }
//
//    /**
//     * Removes a shared directory.
//     */
//    fun removeSharedDirectory(alias: String) {
//        sharedDirectories.remove(alias)
//    }

    /**
     * Clears all shared directories.
     */
    fun clearSharedDirectories() {
        sharedDirectories.clear()
    }

    /**
     * Starts the HTTP server.
     */
    // Update the start() method
    fun start() {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

        // Initialize DLNA handlers
        val deviceUuid = context.getSharedPreferences("anchor_device", Context.MODE_PRIVATE)
            .getString("uuid", null) ?: java.util.UUID.randomUUID().toString().also {
            context.getSharedPreferences("anchor_device", Context.MODE_PRIVATE)
                .edit().putString("uuid", it).apply()
        }

        dlnaHandler = DlnaDescriptionHandler(context, deviceUuid, port)

        server = embeddedServer(Netty, port = port) {
            configureServer()
        }.start(wait = false)

        // Start SSDP announcer
        ssdpAnnouncer = SsdpAnnouncer(context, port).apply {
            start()
        }

        Log.d(TAG, "Server started on port $port")
        AnchorServiceState.addLog("HTTP server started on port $port")
    }
//    fun start() {
//        if (server != null) {
//            Log.w(TAG, "Server already running")
//            return
//        }
//
//        server = embeddedServer(Netty, port = port) {
//            configureServer()
//        }.start(wait = false)
//
//        Log.d(TAG, "Server started on port $port")
//        AnchorServiceState.addLog("HTTP server started on port $port")
//    }

    /**
     * Stops the HTTP server.
     */

// Update the stop() method
    fun stop() {
        ssdpAnnouncer?.stop()
        ssdpAnnouncer = null

        server?.stop(1000, 2000)
        server = null

        dlnaHandler = null
        contentDirectory = null

        Log.d(TAG, "Server stopped")
        AnchorServiceState.addLog("HTTP server stopped")
    }
//    fun stop() {
//        server?.stop(1000, 2000)
//        server = null
//        Log.d(TAG, "Server stopped")
//        AnchorServiceState.addLog("HTTP server stopped")
//    }

    /**
     * Checks if the server is running.
     */
    fun isRunning(): Boolean = server != null

    /**
     * Configures the Ktor application.
     */
    private fun Application.configureServer() {
        // Install plugins
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Range)
            allowHeader(HttpHeaders.AcceptRanges)
            exposeHeader(HttpHeaders.ContentLength)
            exposeHeader(HttpHeaders.AcceptRanges)
            exposeHeader(HttpHeaders.ContentRange)
        }

        install(PartialContent) {
            maxRangeCount = 10
        }

        install(AutoHeadResponse)

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Server error", cause)
                call.respondText(
                    text = "Internal server error: ${cause.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }

        // Configure routes
        routing {
            // Server info endpoint
            get("/") {
                call.respond(getServerInfo())
            }

            get("/api/info") {
                call.respond(getServerInfo())
            }

            // List shared directories
            get("/api/directories") {
                val directories = sharedDirectories.map { (alias, dir) ->
                    mapOf(
                        "alias" to alias,
                        "name" to dir.name,
                        "path" to "/$alias"
                    )
                }
                call.respond(directories)
            }

// ============== DLNA/UPnP Routes ==============

// Device description
            get("/dlna/device.xml") {
                val xml = dlnaHandler?.getDeviceDescription() ?: ""
                call.respondText(xml, ContentType.Text.Xml)
            }

// ContentDirectory service description
            get("/dlna/ContentDirectory.xml") {
                val xml = dlnaHandler?.getContentDirectoryDescription() ?: ""
                call.respondText(xml, ContentType.Text.Xml)
            }

// ConnectionManager service description
            get("/dlna/ConnectionManager.xml") {
                val xml = dlnaHandler?.getConnectionManagerDescription() ?: ""
                call.respondText(xml, ContentType.Text.Xml)
            }

// ContentDirectory control endpoint (SOAP)
            post("/dlna/control/ContentDirectory") {
                val soapRequest = call.receiveText()
                val response = handleContentDirectorySoap(soapRequest)
                call.respondText(response, ContentType.Text.Xml)
            }

// ConnectionManager control endpoint (SOAP)
            post("/dlna/control/ConnectionManager") {
                val soapRequest = call.receiveText()
                val response = handleConnectionManagerSoap(soapRequest)
                call.respondText(response, ContentType.Text.Xml)
            }

// Event subscription endpoints (minimal implementation)
            route("/dlna/event/{service}") {
                handle {
                    call.respondText("", status = HttpStatusCode.OK)
                }
            }

// Icon endpoints
            get("/dlna/icon-120.png") {
                // Return a simple colored square as placeholder
                // In production, you'd serve actual icon files
                call.respond(HttpStatusCode.NotFound)
            }

            get("/dlna/icon-48.png") {
                call.respond(HttpStatusCode.NotFound)
            }
            // Browse directory contents
            get("/api/browse/{alias}/{path...}") {
                val alias =
                    call.parameters["alias"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pathParts = call.parameters.getAll("path") ?: emptyList()
                val relativePath = pathParts.joinToString("/").let {
                    URLDecoder.decode(it, "UTF-8")
                }

                val baseDir = sharedDirectories[alias]
                if (baseDir == null) {
                    call.respond(HttpStatusCode.NotFound, "Directory not found: $alias")
                    return@get
                }

                val targetDir = if (relativePath.isEmpty()) {
                    baseDir
                } else {
                    File(baseDir, relativePath)
                }

                if (!targetDir.exists()) {
                    call.respond(HttpStatusCode.NotFound, "Path not found")
                    return@get
                }

                // Security check: ensure path is within base directory
                if (!targetDir.canonicalPath.startsWith(baseDir.canonicalPath)) {
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@get
                }

                if (targetDir.isDirectory) {
                    val listing = getDirectoryListing(alias, baseDir, targetDir)
                    call.respond(listing)
                } else {
                    // Single file info
                    val mediaFile = fileToMediaFile(alias, baseDir, targetDir)
                    call.respond(mediaFile)
                }
            }

            // Serve file (with byte-range support via PartialContent plugin)
            get("/files/{alias}/{path...}") {
                val alias =
                    call.parameters["alias"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pathParts = call.parameters.getAll("path") ?: emptyList()
                val relativePath = pathParts.joinToString("/").let {
                    URLDecoder.decode(it, "UTF-8")
                }

                val baseDir = sharedDirectories[alias]
                if (baseDir == null) {
                    call.respond(HttpStatusCode.NotFound, "Directory not found")
                    return@get
                }

                val file = File(baseDir, relativePath)

                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound, "File not found")
                    return@get
                }

                // Security check
                if (!file.canonicalPath.startsWith(baseDir.canonicalPath)) {
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@get
                }

                // Log access
                AnchorServiceState.addLog("Serving: ${file.name}")

                // Set content type and serve file
                val mimeType = MimeTypeUtils.getMimeType(file.name)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentType, mimeType)

                call.respondFile(file)
            }

            // Thumbnail endpoint
            get("/thumbnail/{alias}/{path...}") {
                val alias =
                    call.parameters["alias"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pathParts = call.parameters.getAll("path") ?: emptyList()
                val relativePath = pathParts.joinToString("/").let {
                    URLDecoder.decode(it, "UTF-8")
                }

                val baseDir = sharedDirectories[alias]
                if (baseDir == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                val file = File(baseDir, relativePath)

                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                // Security check
                if (!file.canonicalPath.startsWith(baseDir.canonicalPath)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

                val mimeType = MimeTypeUtils.getMimeType(file.name)
                val thumbnail = when {
                    MimeTypeUtils.isVideo(mimeType) ->
                        thumbnailGenerator.getVideoThumbnail(file.absolutePath)

                    MimeTypeUtils.isImage(mimeType) ->
                        thumbnailGenerator.getImageThumbnail(file.absolutePath)

                    MimeTypeUtils.isAudio(mimeType) ->
                        thumbnailGenerator.getAudioAlbumArt(file.absolutePath)

                    else -> null
                }

                if (thumbnail != null) {
                    call.respondBytes(thumbnail, ContentType.Image.JPEG)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Thumbnail not available")
                }
            }

            // Stream endpoint with custom range handling for problematic clients
            get("/stream/{alias}/{path...}") {
                val alias =
                    call.parameters["alias"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pathParts = call.parameters.getAll("path") ?: emptyList()
                val relativePath = pathParts.joinToString("/").let {
                    URLDecoder.decode(it, "UTF-8")
                }

                val baseDir = sharedDirectories[alias]
                if (baseDir == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                val file = File(baseDir, relativePath)

                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                // Security check
                if (!file.canonicalPath.startsWith(baseDir.canonicalPath)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

                val mimeType = MimeTypeUtils.getMimeType(file.name)
                val fileLength = file.length()

                // Parse Range header manually for more control
                val rangeHeader = call.request.headers[HttpHeaders.Range]

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    // Handle range request
                    val rangeSpec = rangeHeader.removePrefix("bytes=")
                    val (startStr, endStr) = rangeSpec.split("-").let {
                        it[0] to it.getOrElse(1) { "" }
                    }

                    val start = startStr.toLongOrNull() ?: 0
                    val end = endStr.toLongOrNull() ?: (fileLength - 1)
                    val contentLength = end - start + 1

                    call.response.status(HttpStatusCode.PartialContent)
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileLength")
                    call.response.header(HttpHeaders.ContentLength, contentLength.toString())
                    call.response.header(HttpHeaders.ContentType, mimeType)

                    call.respond(RangeFileContent(file, start, contentLength))
                } else {
                    // Full file request
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentLength, fileLength.toString())
                    call.response.header(HttpHeaders.ContentType, mimeType)
                    call.respondFile(file)
                }
            }
        }
    }

    /**
     * Gets server information.
     */
    private fun getServerInfo(): ServerInfo {
        var totalFiles = 0
        var totalSize = 0L

        sharedDirectories.values.forEach { dir ->
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalFiles++
                    totalSize += file.length()
                }
            }
        }

        return ServerInfo(
            name = "Anchor Media Server",
            version = "1.0.0",
            deviceName = Build.MODEL,
            sharedDirectories = sharedDirectories.keys.toList(),
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }

    /**
     * Gets directory listing for a path.
     */
    private fun getDirectoryListing(
        alias: String,
        baseDir: File,
        targetDir: File
    ): DirectoryListing {
        val relativePath = targetDir.relativeTo(baseDir).path
        val parentPath = if (relativePath.isNotEmpty()) {
            val parent = File(relativePath).parent
            if (parent != null) "/$alias/$parent" else "/$alias"
        } else {
            null
        }

        val files = targetDir.listFiles()
            ?.filter { !it.isHidden }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { fileToMediaFile(alias, baseDir, it) }
            ?: emptyList()

        val totalSize = files.filter { !it.isDirectory }.sumOf { it.size }

        return DirectoryListing(
            path = "/$alias/$relativePath".replace("//", "/"),
            parentPath = parentPath,
            files = files,
            totalFiles = files.size,
            totalSize = totalSize
        )
    }

    /**
     * Converts a File to MediaFile data class.
     */
    private fun fileToMediaFile(alias: String, baseDir: File, file: File): MediaFile {
        val relativePath = file.relativeTo(baseDir).path
        val mimeType = if (file.isDirectory) "" else MimeTypeUtils.getMimeType(file.name)

        return MediaFile(
            name = file.name,
            path = "/$alias/$relativePath".replace("//", "/"),
            absolutePath = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            mimeType = mimeType,
            lastModified = file.lastModified(),
            mediaType = if (file.isDirectory) {
                MediaType.UNKNOWN
            } else {
                MediaType.fromMimeType(mimeType)
            }
        )
    }


    /**
     * Handles SOAP requests for ContentDirectory service.
     */

// Update the SOAP handler to ensure sync
    private fun handleContentDirectorySoap(soapRequest: String): String {
        // Make sure content directory is synced
        syncContentDirectory()

        Log.d(TAG, "SOAP Request received, length: ${soapRequest.length}")

        return when {
            soapRequest.contains("Browse", ignoreCase = true) -> {
                val objectId = extractSoapValue(soapRequest, "ObjectID") ?: "0"
                val browseFlag =
                    extractSoapValue(soapRequest, "BrowseFlag") ?: "BrowseDirectChildren"
                val filter = extractSoapValue(soapRequest, "Filter") ?: "*"
                val startIndex = extractSoapValue(soapRequest, "StartingIndex")?.toIntOrNull() ?: 0
                val requestedCount =
                    extractSoapValue(soapRequest, "RequestedCount")?.toIntOrNull() ?: 0
                val sortCriteria = extractSoapValue(soapRequest, "SortCriteria") ?: ""

                Log.d(TAG, "Browse request: objectId=$objectId, flag=$browseFlag")

                val result = contentDirectory!!.handleBrowse(
                    objectId, browseFlag, filter, startIndex, requestedCount, sortCriteria
                )

                Log.d(TAG, "Browse result: ${result.numberReturned} items")

                buildBrowseResponse(result)
            }

            soapRequest.contains("GetSystemUpdateID", ignoreCase = true) -> {
                buildGetSystemUpdateIdResponse(contentDirectory?.getSystemUpdateId() ?: 1)
            }

            soapRequest.contains("GetSearchCapabilities", ignoreCase = true) -> {
                buildGetSearchCapabilitiesResponse()
            }

            soapRequest.contains("GetSortCapabilities", ignoreCase = true) -> {
                buildGetSortCapabilitiesResponse()
            }

            else -> {
                Log.w(TAG, "Unknown SOAP action in request")
                buildSoapFault("Unknown action")
            }
        }
    }

    /**
     * Handles SOAP requests for ConnectionManager service.
     */
    private fun handleConnectionManagerSoap(soapRequest: String): String {
        return when {
            soapRequest.contains("GetProtocolInfo", ignoreCase = true) -> {
                val protocolInfo = dlnaHandler?.getProtocolInfo() ?: ""
                buildGetProtocolInfoResponse(protocolInfo)
            }

            soapRequest.contains("GetCurrentConnectionIDs", ignoreCase = true) -> {
                buildGetCurrentConnectionIdsResponse()
            }

            else -> buildSoapFault("Unknown action")
        }
    }

    /**
     * Extracts a value from SOAP XML.
     */
    private fun extractSoapValue(soap: String, tagName: String): String? {
        val regex = Regex("<$tagName[^>]*>([^<]*)</$tagName>", RegexOption.IGNORE_CASE)
        return regex.find(soap)?.groupValues?.get(1)
    }

    /**
     * Builds Browse response SOAP envelope.
     */

// Fix the buildBrowseResponse - the Result should be properly escaped
    private fun buildBrowseResponse(result: ContentDirectoryService.BrowseResult): String {
        // The DIDL-Lite XML needs to be escaped for inclusion in SOAP
        val escapedResult = result.result
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
<Result>$escapedResult</Result>
<NumberReturned>${result.numberReturned}</NumberReturned>
<TotalMatches>${result.totalMatches}</TotalMatches>
<UpdateID>${result.updateId}</UpdateID>
</u:BrowseResponse>
</s:Body>
</s:Envelope>"""
    }

    private fun buildGetSystemUpdateIdResponse(updateId: Int): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetSystemUpdateIDResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
            <Id>$updateId</Id>
        </u:GetSystemUpdateIDResponse>
    </s:Body>
</s:Envelope>"""
    }

    private fun buildGetSearchCapabilitiesResponse(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetSearchCapabilitiesResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
            <SearchCaps></SearchCaps>
        </u:GetSearchCapabilitiesResponse>
    </s:Body>
</s:Envelope>"""
    }

    private fun buildGetSortCapabilitiesResponse(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetSortCapabilitiesResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
            <SortCaps>dc:title</SortCaps>
        </u:GetSortCapabilitiesResponse>
    </s:Body>
</s:Envelope>"""
    }

    private fun buildGetProtocolInfoResponse(protocolInfo: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetProtocolInfoResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">
            <Source>$protocolInfo</Source>
            <Sink></Sink>
        </u:GetProtocolInfoResponse>
    </s:Body>
</s:Envelope>"""
    }

    private fun buildGetCurrentConnectionIdsResponse(): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetCurrentConnectionIDsResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">
            <ConnectionIDs>0</ConnectionIDs>
        </u:GetCurrentConnectionIDsResponse>
    </s:Body>
</s:Envelope>"""
    }

    private fun buildSoapFault(message: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <s:Fault>
            <faultcode>s:Client</faultcode>
            <faultstring>$message</faultstring>
        </s:Fault>
    </s:Body>
</s:Envelope>"""
    }
}


/**
 * Custom content type for serving a range of a file.
 */
private class RangeFileContent(
    private val file: File,
    private val start: Long,
    private val length: Long
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = length

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)

                val buffer = ByteArray(64 * 1024)
                var remaining = length

                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break

                    channel.writeFully(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }
}