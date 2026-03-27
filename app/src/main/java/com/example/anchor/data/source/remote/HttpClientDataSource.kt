package com.example.anchor.data.source.remote

import android.util.Log
import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf
import com.example.anchor.data.dto.DeviceDescriptionDto
import com.example.anchor.data.dto.DirectoryListingDto
import com.example.anchor.data.dto.MediaFileDto
import com.example.anchor.data.dto.ServerInfoDto
import com.example.anchor.data.dto.SharedDirectoryDto
import com.example.anchor.data.source.remote.DeviceDescriptionParser.parseDeviceDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.URL

/**
 * Makes HTTP requests to remote Anchor servers discovered via SSDP.
 *
 * Uses [java.net.URL.readText] (simple, no extra dependency) on [Dispatchers.IO].
 * All methods return [Result] — callers never see raw exceptions.
 *
 * This class has no awareness of domain types; it speaks only DTOs.
 * [DeviceRepositoryImpl] and [RemoteBrowserViewModel] call this and let
 * their mappers convert the results.
 */
class HttpClientDataSource(private val json: Json) {

    companion object {
        private const val TAG = "HttpClientDataSource"
        private const val TIMEOUT_MS = 5_000L
    }

    // ── Anchor API ────────────────────────────────────────────

    /**
     * Fetches server info from GET /api/info on [baseUrl].
     */
    suspend fun getServerInfo(baseUrl: String): Result<ServerInfoDto> =
        get("${baseUrl.trimEnd('/')}/api/info") { body ->
            json.decodeFromString(body)
        }

    /**
     * Fetches the list of shared directories from GET /api/directories.
     */
    suspend fun getDirectories(baseUrl: String): Result<List<SharedDirectoryDto>> =
        get("${baseUrl.trimEnd('/')}/api/directories") { body ->
            json.decodeFromString(body)
        }

    /**
     * Browses a path on a remote Anchor server.
     * [path] is the full path including alias, e.g. "/movies/Action".
     */
    suspend fun browse(baseUrl: String, path: String): Result<DirectoryListingDto> {
        val apiPath = path.trimStart('/')
        return get("${baseUrl.trimEnd('/')}/api/browse/$apiPath") { body ->
            json.decodeFromString(body)
        }
    }

    /**
     * Fetches metadata for a single file on a remote Anchor server.
     */
    suspend fun getFile(baseUrl: String, path: String): Result<MediaFileDto> {
        val apiPath = path.trimStart('/')
        return get("${baseUrl.trimEnd('/')}/api/browse/$apiPath") { body ->
            json.decodeFromString(body)
        }
    }

    // ── UPnP device description ───────────────────────────────

    /**
     * Fetches and parses the UPnP device-description XML at [locationUrl]
     * (the LOCATION header value from an SSDP packet).
     *
     * Returns [Result.Error] when the request times out or XML cannot be parsed.
     */
    suspend fun fetchDeviceDescription(locationUrl: String): Result<DeviceDescriptionDto> =
        withContext(Dispatchers.IO) {
            resultOf {
                val xml = withTimeoutOrNull(TIMEOUT_MS) {
                    URL(locationUrl).readText()
                } ?: throw IllegalStateException("Device description fetch timed out")

                parseDeviceDescription(xml)
                    ?: throw IllegalStateException("Failed to parse device description XML")
            }
        }

    // ── URL helpers ───────────────────────────────────────────

    /**
     * Builds the stream URL for a file on [baseUrl].
     * Use this URL to play media directly in ExoPlayer.
     */
    fun streamUrl(baseUrl: String, filePath: String): String =
        "${baseUrl.trimEnd('/')}/stream/${filePath.trimStart('/')}"

    /**
     * Builds the direct-download URL for a file on [baseUrl].
     */
    fun fileUrl(baseUrl: String, filePath: String): String =
        "${baseUrl.trimEnd('/')}/files/${filePath.trimStart('/')}"

    /**
     * Builds the thumbnail URL for a file on [baseUrl].
     */
    fun thumbnailUrl(baseUrl: String, filePath: String): String =
        "${baseUrl.trimEnd('/')}/thumbnail/${filePath.trimStart('/')}"

    // ── Generic GET ───────────────────────────────────────────

    private suspend fun <T> get(
        url: String,
        parse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        resultOf {
            Log.d(TAG, "GET $url")
            val body = withTimeoutOrNull(TIMEOUT_MS) {
                URL(url).readText()
            } ?: throw IllegalStateException("Request timed out: $url")
            parse(body)
        }
    }
}

// ── DeviceDescriptionParser ───────────────────────────────────
// Kept here to avoid an extra file for a small, focused parser.

private object DeviceDescriptionParser {

    /**
     * Parses UPnP device-description XML using Android's XmlPullParser.
     * Returns null on any parse error.
     */
    fun parseDeviceDescription(xml: String): DeviceDescriptionDto? {
        return try {
            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(java.io.StringReader(xml))

            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var modelDescription = ""
            var udn = ""
            var deviceType = ""
            var presentationUrl = ""
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> currentTag = parser.name
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "friendlyname" -> friendlyName = text
                                "manufacturer" -> manufacturer = text
                                "modelname" -> modelName = text
                                "modeldescription" -> modelDescription = text
                                "udn" -> udn = text
                                "devicetype" -> deviceType = text
                                "presentationurl" -> presentationUrl = text
                            }
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }

            DeviceDescriptionDto(
                friendlyName = friendlyName,
                manufacturer = manufacturer,
                modelName = modelName,
                modelDescription = modelDescription,
                udn = udn,
                deviceType = deviceType,
                presentationUrl = presentationUrl
            )
        } catch (e: Exception) {
            null
        }
    }
}