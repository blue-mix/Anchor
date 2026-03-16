package com.example.anchor.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a discovered UPnP/DLNA device on the network.
 */
@Serializable
data class DiscoveredDevice(
    val usn: String,                          // Unique Service Name (unique identifier)
    val location: String,                      // URL to device description XML
    val serverType: ServerType,                // Type of server discovered
    val friendlyName: String = "",             // Human-readable name
    val manufacturer: String = "",             // Device manufacturer
    val modelName: String = "",                // Device model
    val ipAddress: String = "",                // Extracted IP address
    val port: Int = 0,                         // Extracted port
    val lastSeen: Long = System.currentTimeMillis()
) {

    /**
     * Returns the base URL for accessing this device.
     */
    val baseUrl: String
        get() = if (ipAddress.isNotEmpty() && port > 0) {
            "http://$ipAddress:$port"
        } else {
            location.substringBefore("/", location)
        }

    /**
     * Checks if this device entry is stale (not seen in last 5 minutes).
     */
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastSeen > 5 * 60 * 1000

    /**
     * Display name for UI, preferring friendly name over IP.
     */
    val displayName: String
        get() = friendlyName.ifEmpty {
            if (ipAddress.isNotEmpty()) "Device at $ipAddress" else "Unknown Device"
        }
}

/**
 * Type of server/device discovered.
 */
enum class ServerType {
    ANCHOR,           // Another Anchor instance
    DLNA_MEDIA_SERVER, // Generic DLNA/UPnP media server
    DLNA_RENDERER,    // DLNA renderer (Smart TV, etc.)
    UNKNOWN
}