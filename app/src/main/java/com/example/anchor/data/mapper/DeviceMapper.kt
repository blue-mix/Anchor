package com.example.anchor.data.mapper

import com.example.anchor.data.dto.DeviceDescriptionDto
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import java.net.URL

/**
 * Maps [SsdpMessageDto] and [DeviceDescriptionDto] (data layer) to the
 * domain [Device] model.
 */
object DeviceMapper {

    data class NetworkAddress(val ip: String?, val port: Int?)

    // ── SSDP → Domain (initial stub) ──────────────────────────

    fun SsdpMessageDto.toDeviceStub(sourceIp: String): Device? {
        val locationUrl = location ?: return null
        val deviceUsn = usn ?: return null

        val (ipAddress, portNumber) = extractIpAndPort(locationUrl)

        return Device(
            usn = deviceUsn,
            location = locationUrl,
            serverType = classifyType(searchTarget),
            ipAddress = ipAddress ?: sourceIp,
            port = portNumber ?: 80,
            lastSeen = System.currentTimeMillis()
        )
    }

    // ── Enrich with description XML ───────────────────────────

    fun Device.enrichWithDescription(description: DeviceDescriptionDto): Device = copy(
        friendlyName = description.friendlyName.ifEmpty { friendlyName },
        manufacturer = description.manufacturer,
        modelName = description.modelName,
        serverType = if (description.deviceType.isNotEmpty())
            classifyType(description.deviceType)
        else
            serverType
    )

    // ── Update last-seen timestamp ────────────────────────────

    fun Device.refreshed(): Device = copy(lastSeen = System.currentTimeMillis())

    // ── Classification heuristic ──────────────────────────────

    fun classifyType(searchTarget: String): DeviceType = when {
        searchTarget.contains("anchor", ignoreCase = true) -> DeviceType.ANCHOR
        searchTarget.contains("MediaServer", ignoreCase = true) -> DeviceType.DLNA_MEDIA_SERVER
        searchTarget.contains("ContentDirectory", ignoreCase = true) -> DeviceType.DLNA_MEDIA_SERVER
        searchTarget.contains("MediaRenderer", ignoreCase = true) -> DeviceType.DLNA_RENDERER
        searchTarget.contains("AVTransport", ignoreCase = true) -> DeviceType.DLNA_RENDERER
        else -> DeviceType.UNKNOWN
    }

    // ── Helper ────────────────────────────────────────────────

    fun extractIpAndPort(location: String): NetworkAddress {
        return try {
            val url = URL(location)
            val port = if (url.port == -1) url.defaultPort else url.port
            NetworkAddress(url.host, port)
        } catch (e: Exception) {
            NetworkAddress(null, null)
        }
    }
}
