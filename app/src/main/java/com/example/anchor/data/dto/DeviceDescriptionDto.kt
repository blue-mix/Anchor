package com.example.anchor.data.dto

/**
 * Parsed fields from a UPnP device-description XML document
 * (served at the LOCATION URL from an SSDP packet).
 *
 * DeviceDescriptionParser produces this; DeviceMapper consumes it.
 */
data class DeviceDescriptionDto(
    val friendlyName: String = "",
    val manufacturer: String = "",
    val modelName: String = "",
    val modelDescription: String = "",
    val udn: String = "",
    val deviceType: String = "",
    val presentationUrl: String = ""
)