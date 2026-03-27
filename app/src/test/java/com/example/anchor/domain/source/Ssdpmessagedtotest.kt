package com.example.anchor.data.source

import com.example.anchor.data.dto.SsdpMessageDto
import com.example.anchor.data.dto.SsdpMessageType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SsdpMessageDtoTest {

    // ── Parsing ───────────────────────────────────────────────

    @Nested
    inner class Parsing {

        @Test
        fun `parses NOTIFY message`() {
            val raw = """
                NOTIFY * HTTP/1.1
                HOST: 239.255.255.250:1900
                NT: upnp:rootdevice
                NTS: ssdp:alive
                LOCATION: http://192.168.1.5:8080/dlna/device.xml
                USN: uuid:abc123::upnp:rootdevice
            """.trimIndent()

            val msg = SsdpMessageDto.parse(raw)

            assertThat(msg).isNotNull()
            assertThat(msg!!.type).isEqualTo(SsdpMessageType.NOTIFY)
            assertThat(msg.nt).isEqualTo("upnp:rootdevice")
            assertThat(msg.nts).isEqualTo("ssdp:alive")
            assertThat(msg.location).isEqualTo("http://192.168.1.5:8080/dlna/device.xml")
            assertThat(msg.usn).isEqualTo("uuid:abc123::upnp:rootdevice")
        }

        @Test
        fun `parses M-SEARCH response`() {
            val raw = """
                HTTP/1.1 200 OK
                CACHE-CONTROL: max-age=1800
                ST: urn:schemas-upnp-org:device:MediaServer:1
                USN: uuid:abc::urn:schemas-upnp-org:device:MediaServer:1
                LOCATION: http://192.168.1.5:8080/dlna/device.xml
                SERVER: Android/14 UPnP/1.1 Anchor/1.0
            """.trimIndent()

            val msg = SsdpMessageDto.parse(raw)

            assertThat(msg).isNotNull()
            assertThat(msg!!.type).isEqualTo(SsdpMessageType.RESPONSE)
            assertThat(msg.st).isEqualTo("urn:schemas-upnp-org:device:MediaServer:1")
        }

        @Test
        fun `parses M-SEARCH request`() {
            val raw = """
                M-SEARCH * HTTP/1.1
                HOST: 239.255.255.250:1900
                MAN: "ssdp:discover"
                MX: 3
                ST: ssdp:all
            """.trimIndent()

            val msg = SsdpMessageDto.parse(raw)

            assertThat(msg).isNotNull()
            assertThat(msg!!.type).isEqualTo(SsdpMessageType.M_SEARCH)
            assertThat(msg.st).isEqualTo("ssdp:all")
        }

        @Test
        fun `returns null for unrecognised first line`() {
            val msg = SsdpMessageDto.parse("GARBAGE DATA HERE")
            assertThat(msg).isNull()
        }

        @Test
        fun `returns null for empty input`() {
            assertThat(SsdpMessageDto.parse("")).isNull()
        }

        @Test
        fun `header keys are uppercased`() {
            val raw = """
                HTTP/1.1 200 OK
                location: http://192.168.1.5:8080/dlna/device.xml
                usn: uuid:test
            """.trimIndent()

            val msg = SsdpMessageDto.parse(raw)!!
            // Accessor uses uppercase keys
            assertThat(msg.location).isEqualTo("http://192.168.1.5:8080/dlna/device.xml")
            assertThat(msg.usn).isEqualTo("uuid:test")
        }

        @Test
        fun `parses from byte array`() {
            val raw = "HTTP/1.1 200 OK\r\nUSN: uuid:test\r\nLOCATION: http://1.2.3.4/\r\n"
            val msg = SsdpMessageDto.parse(raw.toByteArray(Charsets.UTF_8))
            assertThat(msg).isNotNull()
            assertThat(msg!!.type).isEqualTo(SsdpMessageType.RESPONSE)
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    @Nested
    inner class Helpers {

        @Test
        fun `isAlive true for ssdp alive NTS`() {
            val msg = makeMsgWithNts("ssdp:alive")
            assertThat(msg.isAlive).isTrue()
            assertThat(msg.isByeBye).isFalse()
        }

        @Test
        fun `isByeBye true for ssdp byebye NTS`() {
            val msg = makeMsgWithNts("ssdp:byebye")
            assertThat(msg.isByeBye).isTrue()
            assertThat(msg.isAlive).isFalse()
        }

        @Test
        fun `searchTarget uses ST for RESPONSE messages`() {
            val msg = SsdpMessageDto(
                type    = SsdpMessageType.RESPONSE,
                headers = mapOf("ST" to "urn:schemas-upnp-org:device:MediaServer:1")
            )
            assertThat(msg.searchTarget)
                .isEqualTo("urn:schemas-upnp-org:device:MediaServer:1")
        }

        @Test
        fun `searchTarget uses NT for NOTIFY messages`() {
            val msg = SsdpMessageDto(
                type    = SsdpMessageType.NOTIFY,
                headers = mapOf("NT" to "upnp:rootdevice")
            )
            assertThat(msg.searchTarget).isEqualTo("upnp:rootdevice")
        }

        @Test
        fun `searchTarget is empty string when neither ST nor NT present`() {
            val msg = SsdpMessageDto(type = SsdpMessageType.NOTIFY, headers = emptyMap())
            assertThat(msg.searchTarget).isEmpty()
        }

        private fun makeMsgWithNts(nts: String) = SsdpMessageDto(
            type    = SsdpMessageType.NOTIFY,
            headers = mapOf("NTS" to nts, "NT" to "upnp:rootdevice")
        )
    }

    // ── Message builders ──────────────────────────────────────

    @Nested
    inner class Builders {

        @Test
        fun `buildMSearch produces valid M-SEARCH packet`() {
            val bytes = SsdpMessageDto.buildMSearch("ssdp:all", mx = 3)
            val text  = String(bytes, Charsets.UTF_8)

            assertThat(text).contains("M-SEARCH * HTTP/1.1")
            assertThat(text).contains("HOST: 239.255.255.250:1900")
            assertThat(text).contains("ST: ssdp:all")
            assertThat(text).contains("MX: 3")
            assertThat(text).contains("MAN: \"ssdp:discover\"")
        }

        @Test
        fun `buildMediaServerSearch targets MediaServer URN`() {
            val text = String(SsdpMessageDto.buildMediaServerSearch(), Charsets.UTF_8)
            assertThat(text).contains("urn:schemas-upnp-org:device:MediaServer:1")
        }

        @Test
        fun `buildAnchorSearch targets Anchor URN`() {
            val text = String(SsdpMessageDto.buildAnchorSearch(), Charsets.UTF_8)
            assertThat(text).contains("urn:schemas-anchor:device:MediaServer:1")
        }

        @Test
        fun `built packet can be parsed back`() {
            val bytes = SsdpMessageDto.buildMSearch()
            val msg   = SsdpMessageDto.parse(bytes)
            assertThat(msg).isNotNull()
            assertThat(msg!!.type).isEqualTo(SsdpMessageType.M_SEARCH)
        }
    }
}