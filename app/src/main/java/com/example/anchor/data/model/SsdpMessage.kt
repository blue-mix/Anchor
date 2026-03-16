// app/src/main/java/com/example/anchor/discovery/SsdpMessage.kt

package com.example.anchor.data.model

/**
 * Parses and represents an SSDP (Simple Service Discovery Protocol) message.
 */
data class SsdpMessage(
    val type: MessageType,
    val headers: Map<String, String>
) {

    enum class MessageType {
        M_SEARCH,    // Discovery request
        NOTIFY,      // Device announcement
        RESPONSE     // Response to M-SEARCH
    }

    // Common SSDP headers
    val location: String? get() = headers["LOCATION"] ?: headers["location"]
    val usn: String? get() = headers["USN"] ?: headers["usn"]
    val st: String? get() = headers["ST"] ?: headers["st"]           // Search Target
    val nt: String? get() = headers["NT"] ?: headers["nt"]           // Notification Type
    val nts: String? get() = headers["NTS"] ?: headers["nts"]        // Notification Sub-Type
    val server: String? get() = headers["SERVER"] ?: headers["server"]
    val cacheControl: String? get() = headers["CACHE-CONTROL"] ?: headers["cache-control"]

    /**
     * Checks if this is a device alive notification.
     */
    val isAlive: Boolean
        get() = nts?.contains("alive", ignoreCase = true) == true

    /**
     * Checks if this is a device byebye (leaving) notification.
     */
    val isByeBye: Boolean
        get() = nts?.contains("byebye", ignoreCase = true) == true

    /**
     * Determines the server type based on ST/NT headers.
     */
    fun getServerType(): ServerType {
        val searchTarget = st ?: nt ?: ""
        return when {
            searchTarget.contains("anchor", ignoreCase = true) -> ServerType.ANCHOR
            searchTarget.contains("MediaServer", ignoreCase = true) -> ServerType.DLNA_MEDIA_SERVER
            searchTarget.contains("ContentDirectory", ignoreCase = true) -> ServerType.DLNA_MEDIA_SERVER
            searchTarget.contains("MediaRenderer", ignoreCase = true) -> ServerType.DLNA_RENDERER
            searchTarget.contains("AVTransport", ignoreCase = true) -> ServerType.DLNA_RENDERER
            else -> ServerType.UNKNOWN
        }
    }

    companion object {

        /**
         * Parses raw SSDP message bytes into an SsdpMessage object.
         */
        fun parse(data: ByteArray): SsdpMessage? {
            return try {
                val message = String(data, Charsets.UTF_8)
                parse(message)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parses raw SSDP message string into an SsdpMessage object.
         */
        fun parse(message: String): SsdpMessage? {
            return try {
                val lines = message.lines()
                if (lines.isEmpty()) return null

                val firstLine = lines[0].uppercase()
                val type = when {
                    firstLine.startsWith("M-SEARCH") -> MessageType.M_SEARCH
                    firstLine.startsWith("NOTIFY") -> MessageType.NOTIFY
                    firstLine.startsWith("HTTP/1.1 200") -> MessageType.RESPONSE
                    else -> return null
                }

                val headers = mutableMapOf<String, String>()
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().uppercase()
                        val value = line.substring(colonIndex + 1).trim()
                        headers[key] = value
                    }
                }

                SsdpMessage(type, headers)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Creates an M-SEARCH discovery request message.
         */
        fun createMSearchMessage(
            searchTarget: String = "ssdp:all",
            mx: Int = 3
        ): ByteArray {
            val message = buildString {
                appendLine("M-SEARCH * HTTP/1.1")
                appendLine("HOST: 239.255.255.250:1900")
                appendLine("MAN: \"ssdp:discover\"")
                appendLine("MX: $mx")
                appendLine("ST: $searchTarget")
                appendLine("USER-AGENT: Anchor/1.0 UPnP/1.1")
                appendLine()
            }
            return message.toByteArray(Charsets.UTF_8)
        }

        /**
         * Creates an M-SEARCH specifically for media servers.
         */
        fun createMediaServerSearch(): ByteArray {
            return createMSearchMessage("urn:schemas-upnp-org:device:MediaServer:1")
        }

        /**
         * Creates an M-SEARCH specifically for Anchor servers.
         */
        fun createAnchorSearch(): ByteArray {
            return createMSearchMessage("urn:schemas-anchor:device:MediaServer:1")
        }
    }
}