package com.example.anchor.server.handler

import android.util.Log
import com.example.anchor.server.DlnaManager
import com.example.anchor.server.dlna.ContentDirectoryService
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText

/**
 * Handles all DLNA/UPnP SOAP endpoints.
 */
class SoapHandler(private val dlnaManager: DlnaManager) {
    companion object {
        private const val TAG = "SoapHandler"
    }

    suspend fun handleContentDirectory(call: ApplicationCall) {
        val contentDirectory = dlnaManager.getContentDirectory() ?: run {
            call.respondText(fault("Service not ready"), ContentType.Text.Xml)
            return
        }

        val soap = call.receiveText()
        val response = when {
            soap.contains("Browse", ignoreCase = true) -> handleBrowse(soap, contentDirectory)
            soap.contains("GetSystemUpdateID", ignoreCase = true) ->
                systemUpdateIdResponse(contentDirectory.getSystemUpdateId())

            soap.contains(
                "GetSearchCapabilities",
                ignoreCase = true
            ) -> searchCapabilitiesResponse()

            soap.contains("GetSortCapabilities", ignoreCase = true) -> sortCapabilitiesResponse()
            else -> {
                Log.w(TAG, "Unknown ContentDirectory SOAP action")
                fault("Unknown action")
            }
        }
        call.respondText(response, ContentType.Text.Xml)
    }

    suspend fun handleConnectionManager(call: ApplicationCall) {
        val dlnaGenerator = dlnaManager.getGenerator() ?: run {
            call.respondText(fault("Service not ready"), ContentType.Text.Xml)
            return
        }

        val soap = call.receiveText()
        val response = when {
            soap.contains("GetProtocolInfo", ignoreCase = true) ->
                protocolInfoResponse(dlnaGenerator.getProtocolInfo())

            soap.contains("GetCurrentConnectionIDs", ignoreCase = true) ->
                currentConnectionIdsResponse()

            else -> fault("Unknown action")
        }
        call.respondText(response, ContentType.Text.Xml)
    }

    // ── SOAP parsing ──────────────────────────────────────────

    private fun handleBrowse(soap: String, contentDirectory: ContentDirectoryService): String {
        val objectId = extractValue(soap, "ObjectID") ?: "0"
        val browseFlag = extractValue(soap, "BrowseFlag") ?: "BrowseDirectChildren"
        val filter = extractValue(soap, "Filter") ?: "*"
        val startIndex = extractValue(soap, "StartingIndex")?.toIntOrNull() ?: 0
        val requestedCount = extractValue(soap, "RequestedCount")?.toIntOrNull() ?: 0
        val sortCriteria = extractValue(soap, "SortCriteria") ?: ""

        Log.d(TAG, "Browse objectId=$objectId flag=$browseFlag")

        val result = contentDirectory.handleBrowse(
            objectId, browseFlag, filter, startIndex, requestedCount, sortCriteria
        )
        return browseResponse(result)
    }

    private fun extractValue(soap: String, tag: String): String? =
        Regex("<$tag[^>]*>([^<]*)</$tag>", RegexOption.IGNORE_CASE)
            .find(soap)?.groupValues?.get(1)

    // ── SOAP response builders ────────────────────────────────

    private fun browseResponse(result: ContentDirectoryService.BrowseResult): String {
        val escaped = result.result
            .replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
        return """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
<Result>$escaped</Result>
<NumberReturned>${result.numberReturned}</NumberReturned>
<TotalMatches>${result.totalMatches}</TotalMatches>
<UpdateID>${result.updateId}</UpdateID>
</u:BrowseResponse></s:Body></s:Envelope>"""
    }

    private fun systemUpdateIdResponse(id: Int) = soapEnvelope(
        "GetSystemUpdateIDResponse",
        "urn:schemas-upnp-org:service:ContentDirectory:1",
        "<Id>$id</Id>"
    )

    private fun searchCapabilitiesResponse() = soapEnvelope(
        "GetSearchCapabilitiesResponse",
        "urn:schemas-upnp-org:service:ContentDirectory:1",
        "<SearchCaps></SearchCaps>"
    )

    private fun sortCapabilitiesResponse() = soapEnvelope(
        "GetSortCapabilitiesResponse",
        "urn:schemas-upnp-org:service:ContentDirectory:1",
        "<SortCaps>dc:title</SortCaps>"
    )

    private fun protocolInfoResponse(info: String) = soapEnvelope(
        "GetProtocolInfoResponse",
        "urn:schemas-upnp-org:service:ConnectionManager:1",
        "<Source>$info</Source><Sink></Sink>"
    )

    private fun currentConnectionIdsResponse() = soapEnvelope(
        "GetCurrentConnectionIDsResponse",
        "urn:schemas-upnp-org:service:ConnectionManager:1",
        "<ConnectionIDs>0</ConnectionIDs>"
    )

    private fun fault(message: String) = """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>$message</faultstring></s:Fault></s:Body></s:Envelope>"""

    private fun soapEnvelope(action: String, xmlns: String, body: String) =
        """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:$action xmlns:u="$xmlns">$body</u:$action></s:Body></s:Envelope>"""
}
