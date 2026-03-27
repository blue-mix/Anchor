package com.example.anchor.data.dto

/**
 * Parsed representation of a raw SSDP UDP packet.
 *
 * This is a pure data container — no serialisation annotations needed because
 * SSDP messages arrive as raw UDP bytes, not JSON.  [SsdpDataSource] produces
 * these; [DeviceMapper] converts them to domain [Device] objects.
 */
data class SsdpMessageDto(
    val type: SsdpMessageType,

    /**
     * All SSDP headers normalised to UPPERCASE keys.
     * Example keys: "LOCATION", "USN", "NT", "NTS", "ST", "SERVER", "CACHE-CONTROL"
     */
    val headers: Map<String, String>
) {
    // ── Header accessors ──────────────────────────────────────

    val location: String? get() = headers["LOCATION"]
    val usn: String? get() = headers["USN"]
    val st: String? get() = headers["ST"]          // Search Target (M-SEARCH response)
    val nt: String? get() = headers["NT"]          // Notification Type (NOTIFY)
    val nts: String? get() = headers["NTS"]         // Notification Sub-Type
    val server: String? get() = headers["SERVER"]
    val cacheControl: String? get() = headers["CACHE-CONTROL"]

    // ── Type helpers ──────────────────────────────────────────

    /** True for ssdp:alive NOTIFY messages. */
    val isAlive: Boolean
        get() = nts?.contains("alive", ignoreCase = true) == true

    /** True for ssdp:byebye NOTIFY messages (device leaving). */
    val isByeBye: Boolean
        get() = nts?.contains("byebye", ignoreCase = true) == true

    /**
     * The effective search/notification target — ST for responses,
     * NT for NOTIFY messages.
     */
    val searchTarget: String
        get() = st ?: nt ?: ""

    companion object {

        // ── Parsing ───────────────────────────────────────────

        /**
         * Parses a raw SSDP UDP payload into an [SsdpMessageDto].
         * Returns null when the first line is not a recognised SSDP verb or status.
         */
        fun parse(data: ByteArray): SsdpMessageDto? =
            parse(String(data, Charsets.UTF_8))

        fun parse(raw: String): SsdpMessageDto? {
            val lines = raw.lines()
            if (lines.isEmpty()) return null

            val firstLine = lines[0].trim().uppercase()
            val type = when {
                firstLine.startsWith("M-SEARCH") -> SsdpMessageType.M_SEARCH
                firstLine.startsWith("NOTIFY") -> SsdpMessageType.NOTIFY
                firstLine.startsWith("HTTP/1.1 200") -> SsdpMessageType.RESPONSE
                else -> return null
            }

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().uppercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            return SsdpMessageDto(type, headers)
        }

        // ── Message builders ──────────────────────────────────

        /**
         * Builds an M-SEARCH discovery request as a UTF-8 byte array.
         */
        fun buildMSearch(
            searchTarget: String = "ssdp:all",
            mx: Int = 3
        ): ByteArray = buildString {
            appendLine("M-SEARCH * HTTP/1.1")
            appendLine("HOST: 239.255.255.250:1900")
            appendLine("MAN: \"ssdp:discover\"")
            appendLine("MX: $mx")
            appendLine("ST: $searchTarget")
            appendLine("USER-AGENT: Anchor/1.0 UPnP/1.1")
            appendLine()
        }.toByteArray(Charsets.UTF_8)

        /** M-SEARCH targeting UPnP media servers only. */
        fun buildMediaServerSearch(): ByteArray =
            buildMSearch("urn:schemas-upnp-org:device:MediaServer:1")

        /** M-SEARCH targeting Anchor servers specifically. */
        fun buildAnchorSearch(): ByteArray =
            buildMSearch("urn:schemas-anchor:device:MediaServer:1")
    }
}

enum class SsdpMessageType {
    M_SEARCH,   // Outgoing discovery request
    NOTIFY,     // Incoming device announcement
    RESPONSE    // Response to our M-SEARCH
}
