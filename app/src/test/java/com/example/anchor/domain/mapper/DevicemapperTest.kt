package com.example.anchor.data.mapper

import com.example.anchor.data.dto.DeviceDescriptionDto
import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.dto.SsdpMessageType
import com.example.anchor.data.mapper.DeviceMapper.classifyType
import com.example.anchor.data.mapper.DeviceMapper.enrichWithDescription
import com.example.anchor.data.mapper.extractIpAndPort
import com.example.anchor.data.mapper.DeviceMapper.refreshed
import com.example.anchor.data.mapper.DeviceMapper.toDeviceStub
import com.example.anchor.domain.model.Device
import com.example.anchor.domain.model.DeviceType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DeviceMapperTest {

    // ── classifyType ──────────────────────────────────────────

    @Nested
    inner class ClassifyType {

        @Test
        fun `anchor keyword maps to ANCHOR`() {
            assertThat(classifyType("urn:schemas-anchor:device:MediaServer:1"))
                .isEqualTo(DeviceType.ANCHOR)
        }

        @Test
        fun `MediaServer maps to DLNA_MEDIA_SERVER`() {
            assertThat(classifyType("urn:schemas-upnp-org:device:MediaServer:1"))
                .isEqualTo(DeviceType.DLNA_MEDIA_SERVER)
        }

        @Test
        fun `ContentDirectory maps to DLNA_MEDIA_SERVER`() {
            assertThat(classifyType("urn:schemas-upnp-org:service:ContentDirectory:1"))
                .isEqualTo(DeviceType.DLNA_MEDIA_SERVER)
        }

        @Test
        fun `MediaRenderer maps to DLNA_RENDERER`() {
            assertThat(classifyType("urn:schemas-upnp-org:device:MediaRenderer:1"))
                .isEqualTo(DeviceType.DLNA_RENDERER)
        }

        @Test
        fun `AVTransport maps to DLNA_RENDERER`() {
            assertThat(classifyType("urn:schemas-upnp-org:service:AVTransport:1"))
                .isEqualTo(DeviceType.DLNA_RENDERER)
        }

        @Test
        fun `unrecognised string maps to UNKNOWN`() {
            assertThat(classifyType("ssdp:all")).isEqualTo(DeviceType.UNKNOWN)
            assertThat(classifyType("upnp:rootdevice")).isEqualTo(DeviceType.UNKNOWN)
            assertThat(classifyType("")).isEqualTo(DeviceType.UNKNOWN)
        }

        @Test
        fun `matching is case-insensitive`() {
            assertThat(classifyType("urn:schemas-ANCHOR:device:MediaServer:1"))
                .isEqualTo(DeviceType.ANCHOR)
            assertThat(classifyType("MEDIASERVER")).isEqualTo(DeviceType.DLNA_MEDIA_SERVER)
        }
    }

    // ── extractIpAndPort ──────────────────────────────────────

    @Nested
    inner class ExtractIpAndPort {

        @Test
        fun `extracts ip and port from http url`() {
            val (ip, port) = extractIpAndPort("http://192.168.1.5:8080/dlna/device.xml")
            assertThat(ip).isEqualTo("192.168.1.5")
            assertThat(port).isEqualTo(8080)
        }

        @Test
        fun `uses default port 80 when no port specified`() {
            val (ip, port) = extractIpAndPort("http://192.168.1.5/dlna/device.xml")
            assertThat(ip).isEqualTo("192.168.1.5")
            assertThat(port).isEqualTo(80)
        }

        @Test
        fun `returns null pair for malformed url`() {
            val (ip, port) = extractIpAndPort("not-a-url")
            assertThat(ip).isNull()
            assertThat(port).isNull()
        }

        @Test
        fun `returns null pair for empty string`() {
            val (ip, port) = extractIpAndPort("")
            assertThat(ip).isNull()
            assertThat(port).isNull()
        }
    }

    // ── toDeviceStub ──────────────────────────────────────────

    @Nested
    inner class ToDeviceStub {

        @Test
        fun `returns null when location header is missing`() {
            val msg = SsdpMessageDto(
                type    = SsdpMessageType.RESPONSE,
                headers = mapOf("USN" to "uuid:1234::upnp:rootdevice")
            )
            assertThat(msg.toDeviceStub("192.168.1.1")).isNull()
        }

        @Test
        fun `returns null when usn header is missing`() {
            val msg = SsdpMessageDto(
                type    = SsdpMessageType.RESPONSE,
                headers = mapOf("LOCATION" to "http://192.168.1.5:8080/dlna/device.xml")
            )
            assertThat(msg.toDeviceStub("192.168.1.1")).isNull()
        }

        @Test
        fun `creates stub with extracted ip and port`() {
            val msg = SsdpMessageDto(
                type = SsdpMessageType.RESPONSE,
                headers = mapOf(
                    "LOCATION" to "http://192.168.1.5:8080/dlna/device.xml",
                    "USN"      to "uuid:abc::urn:schemas-upnp-org:device:MediaServer:1",
                    "ST"       to "urn:schemas-upnp-org:device:MediaServer:1"
                )
            )
            val stub = msg.toDeviceStub("192.168.1.99")

            assertThat(stub).isNotNull()
            assertThat(stub!!.ipAddress).isEqualTo("192.168.1.5")
            assertThat(stub.port).isEqualTo(8080)
            assertThat(stub.serverType).isEqualTo(DeviceType.DLNA_MEDIA_SERVER)
        }

        @Test
        fun `falls back to sourceIp when location has no parseable host`() {
            val msg = SsdpMessageDto(
                type = SsdpMessageType.RESPONSE,
                headers = mapOf(
                    "LOCATION" to "bad-url",
                    "USN"      to "uuid:xyz::upnp:rootdevice"
                )
            )
            val stub = msg.toDeviceStub("10.0.0.1")

            assertThat(stub).isNotNull()
            assertThat(stub!!.ipAddress).isEqualTo("10.0.0.1")
        }

        @Test
        fun `classifies anchor server correctly`() {
            val msg = SsdpMessageDto(
                type = SsdpMessageType.RESPONSE,
                headers = mapOf(
                    "LOCATION" to "http://192.168.1.5:8080/dlna/device.xml",
                    "USN"      to "uuid:abc::urn:schemas-anchor:device:MediaServer:1",
                    "ST"       to "urn:schemas-anchor:device:MediaServer:1"
                )
            )
            val stub = msg.toDeviceStub("192.168.1.5")
            assertThat(stub!!.serverType).isEqualTo(DeviceType.ANCHOR)
        }
    }

    // ── enrichWithDescription ─────────────────────────────────

    @Nested
    inner class EnrichWithDescription {

        private val baseDevice = Device(
            usn        = "uuid:abc::upnp:rootdevice",
            location   = "http://192.168.1.5:8080/dlna/device.xml",
            serverType = DeviceType.UNKNOWN,
            ipAddress  = "192.168.1.5",
            port       = 8080
        )

        @Test
        fun `fills friendly name from description`() {
            val desc = DeviceDescriptionDto(friendlyName = "Living Room TV")
            val enriched = baseDevice.enrichWithDescription(desc)
            assertThat(enriched.friendlyName).isEqualTo("Living Room TV")
        }

        @Test
        fun `fills manufacturer and model`() {
            val desc = DeviceDescriptionDto(
                manufacturer = "Samsung",
                modelName    = "QLED 4K"
            )
            val enriched = baseDevice.enrichWithDescription(desc)
            assertThat(enriched.manufacturer).isEqualTo("Samsung")
            assertThat(enriched.modelName).isEqualTo("QLED 4K")
        }

        @Test
        fun `re-classifies serverType from deviceType xml field`() {
            val desc = DeviceDescriptionDto(
                deviceType = "urn:schemas-upnp-org:device:MediaServer:1"
            )
            val enriched = baseDevice.enrichWithDescription(desc)
            assertThat(enriched.serverType).isEqualTo(DeviceType.DLNA_MEDIA_SERVER)
        }

        @Test
        fun `does not overwrite friendly name when description has empty name`() {
            val deviceWithName = baseDevice.copy(friendlyName = "Existing Name")
            val desc = DeviceDescriptionDto(friendlyName = "")
            val enriched = deviceWithName.enrichWithDescription(desc)
            assertThat(enriched.friendlyName).isEqualTo("Existing Name")
        }
    }

    // ── refreshed ─────────────────────────────────────────────

    @Nested
    inner class Refreshed {

        @Test
        fun `updates lastSeen to current time`() {
            val old = Device(
                usn        = "uuid:abc",
                location   = "http://192.168.1.1/dlna/device.xml",
                serverType = DeviceType.ANCHOR,
                lastSeen   = 0L
            )
            val before  = System.currentTimeMillis()
            val updated = old.refreshed()
            val after   = System.currentTimeMillis()

            assertThat(updated.lastSeen).isAtLeast(before)
            assertThat(updated.lastSeen).isAtMost(after)
        }

        @Test
        fun `preserves all other fields`() {
            val device = Device(
                usn          = "uuid:abc",
                location     = "http://192.168.1.1/dlna/device.xml",
                serverType   = DeviceType.ANCHOR,
                friendlyName = "My Server",
                manufacturer = "Anchor",
                modelName    = "1.0",
                ipAddress    = "192.168.1.1",
                port         = 8080,
                lastSeen     = 0L
            )
            val refreshed = device.refreshed()

            assertThat(refreshed.usn).isEqualTo(device.usn)
            assertThat(refreshed.friendlyName).isEqualTo(device.friendlyName)
            assertThat(refreshed.serverType).isEqualTo(device.serverType)
        }
    }
}