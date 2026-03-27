package com.example.anchor.data.mapper

import com.example.anchor.data.dto.DeviceDescriptionDto
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.mapper.DeviceMapper.enrichWithDescription
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import java.net.URL

/**
 * Maps [SsdpMessageDto] and [DeviceDescriptionDto] (data layer) to the
 * domain [Device] model.
 *
 * Keeps all the classification heuristics in one place so [SsdpDataSource]
 * and [HttpClientDataSource] stay concerned only with I/O.
 */
object DeviceMapper {

    // ── SSDP → Domain (initial stub) ──────────────────────────

    /**
     * Creates a minimal [Device] from an SSDP packet.
     * The friendly name, manufacturer, and model are filled in later when
     * [enrichWithDescription] is called after fetching the description XML.
     *
     * @param message    Parsed SSDP packet.
     * @param sourceIp   IP address of the UDP packet sender.
     * @return null when the packet lacks a LOCATION or USN header.
     */
    fun SsdpMessageDto.toDeviceStub(sourceIp: String): Device? {
        val location = location ?: return null
        val usn = usn ?: return null

        val (ip, port) = extractIpAndPort(location)

        return Device(
            usn = usn,
            location = location,
            serverType = classifyType(searchTarget),
            ipAddress = ip ?: sourceIp,
            port = port ?: 80,
            lastSeen = System.currentTimeMillis()
        )
    }

    // ── Enrich with description XML ───────────────────────────

    /**
     * Returns a copy of [device] updated with fields parsed from the
     * device-description XML ([DeviceDescriptionDto]).
     */
    fun Device.enrichWithDescription(description: DeviceDescriptionDto): Device = copy(
        friendlyName = description.friendlyName.ifEmpty { friendlyName },
        manufacturer = description.manufacturer,
        modelName = description.modelName,
        // Re-classify using the definitive deviceType from the XML
        serverType = if (description.deviceType.isNotEmpty())
            classifyType(description.deviceType)
        else
            serverType
    )

    // ── Update last-seen timestamp ────────────────────────────

    /**
     * Returns a copy of [device] with [Device.lastSeen] set to now.
     * Called when a repeated SSDP alive packet arrives.
     */
    fun Device.refreshed(): Device = copy(lastSeen = System.currentTimeMillis())

    // ── Classification heuristic ──────────────────────────────

    /**
     * Maps a UPnP search-target / notification-type / device-type string
     * to a [DeviceType].
     *
     * Anchor is detected by its custom URN; DLNA types are matched by the
     * standard UPnP strings.
     */
    fun classifyType(searchTarget: String): DeviceType = when {
        searchTarget.contains("anchor", ignoreCase = true) -> DeviceType.ANCHOR
        searchTarget.contains("MediaServer", ignoreCase = true) -> DeviceType.DLNA_MEDIA_SERVER
        searchTarget.contains("ContentDirectory", ignoreCase = true) -> DeviceType.DLNA_MEDIA_SERVER
        searchTarget.contains("MediaRenderer", ignoreCase = true) -> DeviceType.DLNA_RENDERER
        searchTarget.contains("AVTransport", ignoreCase = true) -> DeviceType.DLNA_RENDERER
        else -> DeviceType.UNKNOWN
    }

    // ── Helper ────────────────────────────────────────────────

    /**
     * Extracts the host and port from a URL string.
     * Returns (null, null) when the URL cannot be parsed.
     */
    fun extractIpAndPort(location: String): Pair<String?, Int?> = try {
        val url = URL(location)
        val port = if (url.port == -1) url.defaultPort else url.port
        url.host to port
    } catch (e: Exception) {
        null to null
    }
}