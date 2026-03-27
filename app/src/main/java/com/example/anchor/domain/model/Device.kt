package com.example.anchor.domain.model

/**
 * Domain model for a UPnP/SSDP device discovered on the local network.
 *
 * The data layer maps raw SSDP packets and device-description XML into
 * this type; ViewModels and use-cases never see the raw wire format.
 */
data class Device(
    /**
     * UPnP Unique Service Name — the stable identifier for this device/service
     * combination. Used as the map key in the discovery cache.
     * Example: "uuid:550e8400-e29b-41d4-a716-446655440000::upnp:rootdevice"
     */
    val usn: String,

    /**
     * URL to the device's UPnP description XML.
     * Example: "http://192.168.1.5:8080/dlna/device.xml"
     */
    val location: String,

    /** Classified server/renderer type. */
    val serverType: DeviceType,

    /** Human-readable name from the UPnP description (friendlyName element). */
    val friendlyName: String = "",

    /** Device manufacturer string from UPnP description. */
    val manufacturer: String = "",

    /** Device model name from UPnP description. */
    val modelName: String = "",

    /** Extracted IP address from [location]. */
    val ipAddress: String = "",

    /** Extracted port from [location], defaulting to 80 if not present. */
    val port: Int = 80,

    /**
     * Epoch-millis timestamp of the last SSDP packet received from this device.
     * Used to expire stale entries.
     */
    val lastSeen: Long = System.currentTimeMillis()
) {

    // ── Derived helpers ───────────────────────────────────────

    /**
     * Base HTTP URL for this device's Anchor API, built from [ipAddress] and [port].
     * Falls back to parsing [location] when IP/port are not available.
     */
    val baseUrl: String
        get() = if (ipAddress.isNotEmpty() && port > 0) {
            "http://$ipAddress:$port"
        } else {
            // Find the 3rd slash (after http://host:port/)
            val firstSlashAfterScheme = location.indexOf('/', 8)
            if (firstSlashAfterScheme != -1) {
                location.substring(0, firstSlashAfterScheme)
            } else {
                location
            }
        }

    /**
     * Name suitable for display in the discovery list.
     * Prefers [friendlyName]; falls back to the IP address, then "Unknown Device".
     */
    val displayName: String
        get() = when {
            friendlyName.isNotEmpty() -> friendlyName
            ipAddress.isNotEmpty() -> "Device at $ipAddress"
            else -> "Unknown Device"
        }

    /**
     * Returns true when this entry has not been seen for more than 5 minutes.
     */
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastSeen > STALE_THRESHOLD_MS

    /**
     * Returns true when this device runs the Anchor server (vs. a generic DLNA device).
     */
    val isAnchorServer: Boolean
        get() = serverType == DeviceType.ANCHOR

    companion object {
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1_000L
    }
}

// ── DeviceType ────────────────────────────────────────────────

/**
 * Broad classification of a discovered UPnP device.
 */
enum class DeviceType {
    /** Another Anchor instance running on the local network. */
    ANCHOR,

    /** A generic UPnP/DLNA media server (e.g. Plex, Kodi, NAS). */
    DLNA_MEDIA_SERVER,

    /** A DLNA renderer — a playback target (Smart TV, AV receiver). */
    DLNA_RENDERER,

    /** Discovered but not classified as any of the above. */
    UNKNOWN
}