package com.example.anchor.data.source.remote

import android.util.Log
import com.example.anchor.core.config.AnchorConfig
import com.example.anchor.core.config.AnchorConstants
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
import java.net.InetAddress
import java.net.URL

/**
 * Makes HTTP requests to remote Anchor servers discovered via SSDP.
 */
class HttpClientDataSource(private val json: Json) {

    companion object {
        private const val TAG = "HttpClientDataSource"
    }

    // ── Anchor API ────────────────────────────────────────────

    suspend fun getServerInfo(baseUrl: String): Result<ServerInfoDto> =
        get("${baseUrl.trimEnd('/')}/api/info") { body ->
            json.decodeFromString(body)
        }

    suspend fun getDirectories(baseUrl: String): Result<List<SharedDirectoryDto>> =
        get("${baseUrl.trimEnd('/')}/api/directories") { body ->
            json.decodeFromString(body)
        }

    suspend fun browse(baseUrl: String, path: String): Result<DirectoryListingDto> {
        val apiPath = path.trimStart('/')
        return get("${baseUrl.trimEnd('/')}/api/browse/$apiPath") { body ->
            json.decodeFromString(body)
        }
    }

    suspend fun getFile(baseUrl: String, path: String): Result<MediaFileDto> {
        val apiPath = path.trimStart('/')
        return get("${baseUrl.trimEnd('/')}/api/browse/$apiPath") { body ->
            json.decodeFromString(body)
        }
    }

    // ── UPnP device description ───────────────────────────────

    suspend fun fetchDeviceDescription(locationUrl: String): Result<DeviceDescriptionDto> =
        withContext(Dispatchers.IO) {
            resultOf {
                require(validateUrl(locationUrl)) {
                    "Invalid or unsafe URL: $locationUrl"
                }

                val xml = withTimeoutOrNull(AnchorConfig.Server.TIMEOUT_MS) {
                    URL(locationUrl).readText()
                } ?: throw IllegalStateException("Device description fetch timed out")

                parseDeviceDescription(xml)
                    ?: throw IllegalStateException("Failed to parse device description XML")
            }
        }

    // ── SSRF Validation ───────────────────────────────────────

    private fun validateUrl(url: String): Boolean {
        val parsed = try {
            URL(url)
        } catch (e: Exception) {
            return false
        }

        if (parsed.protocol !in listOf("http", "https")) return false

        val address = try {
            InetAddress.getByName(parsed.host)
        } catch (e: Exception) {
            return false
        }

        if (address.isLoopbackAddress) return false
        
        val hostAddress = address.hostAddress ?: return false
        if (hostAddress.startsWith("127.") || hostAddress.startsWith("169.254.")) return false

        // Allow LAN ranges for UPnP discovery
        return address.isSiteLocalAddress || !address.isLoopbackAddress
    }

    // ── Generic GET ───────────────────────────────────────────

    private suspend fun <T> get(
        url: String,
        parse: (String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        resultOf {
            Log.d(TAG, "GET $url")
            val body = withTimeoutOrNull(AnchorConfig.Server.TIMEOUT_MS) {
                URL(url).readText()
            } ?: throw IllegalStateException("Request timed out: $url")
            parse(body)
        }
    }
}

// ── DeviceDescriptionParser ───────────────────────────────────

private object DeviceDescriptionParser {

    fun parseDeviceDescription(xml: String): DeviceDescriptionDto? {
        return try {
            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            
            // XXE Protection using constants
            try {
                parser.setFeature(AnchorConstants.Xml.FEATURE_EXTERNAL_GENERAL_ENTITIES, false)
                parser.setFeature(AnchorConstants.Xml.FEATURE_EXTERNAL_PARAMETER_ENTITIES, false)
                parser.setFeature(AnchorConstants.Xml.FEATURE_DISALLOW_DOCTYPE, true)
            } catch (e: Exception) {}
            
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
