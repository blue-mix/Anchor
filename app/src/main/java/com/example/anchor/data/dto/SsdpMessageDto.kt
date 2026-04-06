package com.example.anchor.data.dto

import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf

/**
 * Parsed representation of a raw SSDP UDP packet.
 */
data class SsdpMessageDto(
    val type: SsdpMessageType,
    val headers: Map<String, String>
) {
    val location: String? get() = headers["LOCATION"]
    val usn: String? get() = headers["USN"]
    val st: String? get() = headers["ST"]
    val nt: String? get() = headers["NT"]
    val nts: String? get() = headers["NTS"]
    val server: String? get() = headers["SERVER"]

    val isAlive: Boolean
        get() = nts?.contains("alive", ignoreCase = true) == true

    val isByeBye: Boolean
        get() = nts?.contains("byebye", ignoreCase = true) == true

    val searchTarget: String
        get() = st ?: nt ?: ""

    companion object {
        fun parse(data: ByteArray): Result<SsdpMessageDto> =
            parse(String(data, Charsets.UTF_8))

        fun parse(raw: String): Result<SsdpMessageDto> = resultOf {
            val lines = raw.lineSequence()
            val firstLine = lines.firstOrNull()?.trim()?.uppercase()
                ?: throw IllegalArgumentException("Empty SSDP message")

            val type = when {
                firstLine.startsWith("M-SEARCH") -> SsdpMessageType.M_SEARCH
                firstLine.startsWith("NOTIFY") -> SsdpMessageType.NOTIFY
                firstLine.startsWith("HTTP/1.1 200") -> SsdpMessageType.RESPONSE
                else -> throw IllegalArgumentException("Unknown SSDP verb: $firstLine")
            }

            val headers = mutableMapOf<String, String>()
            lines.drop(1).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach

                val colon = trimmed.indexOf(':')
                if (colon > 0) {
                    val key = trimmed.substring(0, colon).trim().uppercase()
                    val value = trimmed.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            SsdpMessageDto(type, headers)
        }

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
    }
}

enum class SsdpMessageType {
    M_SEARCH, NOTIFY, RESPONSE
}
