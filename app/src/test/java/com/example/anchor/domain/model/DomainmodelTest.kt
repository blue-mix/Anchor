package com.example.anchor.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// ── Device ────────────────────────────────────────────────────

class DeviceTest {

    private fun makeDevice(
        usn: String = "uuid:test::upnp:rootdevice",
        location: String = "http://192.168.1.5:8080/dlna/device.xml",
        serverType: DeviceType = DeviceType.ANCHOR,
        friendlyName: String = "",
        ipAddress: String = "192.168.1.5",
        port: Int = 8080,
        lastSeen: Long = System.currentTimeMillis()
    ) = Device(usn, location, serverType, friendlyName, "", "", ipAddress, port, lastSeen)

    @Nested
    inner class BaseUrl {

        @Test
        fun `builds base url from ip and port`() {
            val device = makeDevice(ipAddress = "192.168.1.5", port = 8080)
            assertThat(device.baseUrl).isEqualTo("http://192.168.1.5:8080")
        }

        @Test
        fun `falls back to location when ip is empty`() {
            val device = makeDevice(
                location  = "http://10.0.0.1:9090/dlna/device.xml",
                ipAddress = "",
                port      = 0
            )
            assertThat(device.baseUrl).startsWith("http://10.0.0.1")
        }
    }

    @Nested
    inner class DisplayName {

        @Test
        fun `returns friendlyName when set`() {
            val device = makeDevice(friendlyName = "My Anchor Server")
            assertThat(device.displayName).isEqualTo("My Anchor Server")
        }

        @Test
        fun `falls back to Device at IP when friendlyName is empty`() {
            val device = makeDevice(friendlyName = "", ipAddress = "192.168.1.5")
            assertThat(device.displayName).isEqualTo("Device at 192.168.1.5")
        }

        @Test
        fun `falls back to Unknown Device when both empty`() {
            val device = makeDevice(friendlyName = "", ipAddress = "")
            assertThat(device.displayName).isEqualTo("Unknown Device")
        }
    }

    @Nested
    inner class StaleCheck {

        @Test
        fun `isStale false for recently seen device`() {
            val device = makeDevice(lastSeen = System.currentTimeMillis())
            assertThat(device.isStale).isFalse()
        }

        @Test
        fun `isStale true for device seen over 5 minutes ago`() {
            val device = makeDevice(lastSeen = System.currentTimeMillis() - 6 * 60_000L)
            assertThat(device.isStale).isTrue()
        }
    }

    @Nested
    inner class AnchorCheck {

        @Test
        fun `isAnchorServer true for ANCHOR type`() {
            val device = makeDevice(serverType = DeviceType.ANCHOR)
            assertThat(device.isAnchorServer).isTrue()
        }

        @Test
        fun `isAnchorServer false for DLNA type`() {
            val device = makeDevice(serverType = DeviceType.DLNA_MEDIA_SERVER)
            assertThat(device.isAnchorServer).isFalse()
        }
    }
}

// ── MediaItem ────────────────────────────────────────────────

class MediaItemTest {

    private fun makeItem(
        name: String       = "file.mp4",
        path: String       = "/movies/file.mp4",
        isDirectory: Boolean = false,
        size: Long         = 0L,
        mimeType: String   = "video/mp4",
        mediaType: MediaType = MediaType.VIDEO
    ) = MediaItem(
        name         = name,
        path         = path,
        absolutePath = "/storage$path",
        isDirectory  = isDirectory,
        size         = size,
        mimeType     = mimeType,
        mediaType    = mediaType
    )

    @Nested
    inner class FormattedSize {

        @Test
        fun `returns empty string for directory`() {
            val item = makeItem(isDirectory = true, size = 0L)
            assertThat(item.formattedSize).isEmpty()
        }

        @Test
        fun `formats bytes`() {
            assertThat(makeItem(size = 512L).formattedSize).isEqualTo("512 B")
        }

        @Test
        fun `formats kilobytes`() {
            assertThat(makeItem(size = 2_048L).formattedSize).contains("KB")
        }

        @Test
        fun `formats megabytes`() {
            assertThat(makeItem(size = 5 * 1_048_576L).formattedSize).contains("MB")
        }

        @Test
        fun `formats gigabytes`() {
            assertThat(makeItem(size = 2 * 1_073_741_824L).formattedSize).contains("GB")
        }
    }

    @Nested
    inner class EncodedPath {

        @Test
        fun `encodes spaces in path segments`() {
            val item = makeItem(path = "/movies/My Film.mp4")
            assertThat(item.encodedPath).doesNotContain(" ")
            assertThat(item.encodedPath).contains("%20")
        }

        @Test
        fun `preserves slashes`() {
            val item = makeItem(path = "/movies/Action/film.mp4")
            assertThat(item.encodedPath).isEqualTo("/movies/Action/film.mp4")
        }
    }

    @Nested
    inner class StreamableCheck {

        @Test
        fun `isStreamable true for video`() {
            assertThat(makeItem(mediaType = MediaType.VIDEO).isStreamable).isTrue()
        }

        @Test
        fun `isStreamable true for audio`() {
            assertThat(makeItem(mediaType = MediaType.AUDIO).isStreamable).isTrue()
        }

        @Test
        fun `isStreamable false for image`() {
            assertThat(makeItem(mediaType = MediaType.IMAGE).isStreamable).isFalse()
        }

        @Test
        fun `isStreamable false for document`() {
            assertThat(makeItem(mediaType = MediaType.DOCUMENT).isStreamable).isFalse()
        }
    }

    @Nested
    inner class ThumbnailCheck {

        @Test
        fun `hasThumbnail true for video audio image`() {
            assertThat(makeItem(mediaType = MediaType.VIDEO).hasThumbnail).isTrue()
            assertThat(makeItem(mediaType = MediaType.AUDIO).hasThumbnail).isTrue()
            assertThat(makeItem(mediaType = MediaType.IMAGE).hasThumbnail).isTrue()
        }

        @Test
        fun `hasThumbnail false for document and unknown`() {
            assertThat(makeItem(mediaType = MediaType.DOCUMENT).hasThumbnail).isFalse()
            assertThat(makeItem(mediaType = MediaType.UNKNOWN).hasThumbnail).isFalse()
        }
    }
}

// ── MediaType ─────────────────────────────────────────────────

class MediaTypeTest {

    @Nested
    inner class FromMimeType {

        @Test
        fun `video mime resolves to VIDEO`() {
            assertThat(MediaType.fromMimeType("video/mp4")).isEqualTo(MediaType.VIDEO)
            assertThat(MediaType.fromMimeType("video/x-matroska")).isEqualTo(MediaType.VIDEO)
        }

        @Test
        fun `audio mime resolves to AUDIO`() {
            assertThat(MediaType.fromMimeType("audio/mpeg")).isEqualTo(MediaType.AUDIO)
            assertThat(MediaType.fromMimeType("audio/flac")).isEqualTo(MediaType.AUDIO)
        }

        @Test
        fun `image mime resolves to IMAGE`() {
            assertThat(MediaType.fromMimeType("image/jpeg")).isEqualTo(MediaType.IMAGE)
        }

        @Test
        fun `pdf resolves to DOCUMENT`() {
            assertThat(MediaType.fromMimeType("application/pdf")).isEqualTo(MediaType.DOCUMENT)
        }

        @Test
        fun `text resolves to DOCUMENT`() {
            assertThat(MediaType.fromMimeType("text/plain")).isEqualTo(MediaType.DOCUMENT)
        }

        @Test
        fun `unknown resolves to UNKNOWN`() {
            assertThat(MediaType.fromMimeType("application/octet-stream"))
                .isEqualTo(MediaType.UNKNOWN)
        }
    }

    @Nested
    inner class FromExtension {

//        @Test
//        fun `video extensions resolve to VIDEO`() {
//            listOf("mp4", "mkv", "avi", "mov", "webm", "ts").forEach { ext ->
//                assertThat(MediaType.fromExtension(ext))
//                    .named("extension: $ext")
//                    .isEqualTo(MediaType.VIDEO)
//            }
//        }
//
//        @Test
//        fun `audio extensions resolve to AUDIO`() {
//            listOf("mp3", "flac", "wav", "aac", "ogg", "opus").forEach { ext ->
//                assertThat(MediaType.fromExtension(ext))
//                    .named("extension: $ext")
//                    .isEqualTo(MediaType.AUDIO)
//            }
//        }
//
//        @Test
//        fun `image extensions resolve to IMAGE`() {
//            listOf("jpg", "jpeg", "png", "gif", "webp", "heic").forEach { ext ->
//                assertThat(MediaType.fromExtension(ext))
//                    .named("extension: $ext")
//                    .isEqualTo(MediaType.IMAGE)
//            }
//        }

        @Test
        fun `extension matching is case-insensitive`() {
            assertThat(MediaType.fromExtension("MP4")).isEqualTo(MediaType.VIDEO)
            assertThat(MediaType.fromExtension("FLAC")).isEqualTo(MediaType.AUDIO)
        }

        @Test
        fun `unknown extension resolves to UNKNOWN`() {
            assertThat(MediaType.fromExtension("xyz")).isEqualTo(MediaType.UNKNOWN)
        }
    }
}

// ── ServerConfig ──────────────────────────────────────────────

class ServerConfigTest {

    @Test
    fun `default port is 8080`() {
        assertThat(ServerConfig().port).isEqualTo(8080)
    }

    @Test
    fun `throws on invalid port`() {
        val thrown = runCatching { ServerConfig(port = 80) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `withSafePort clamps to valid range`() {
        val config = ServerConfig()
        assertThat(config.withSafePort(80).port).isEqualTo(ServerConfig.MIN_PORT)
        assertThat(config.withSafePort(99_999).port).isEqualTo(ServerConfig.MAX_PORT)
        assertThat(config.withSafePort(9090).port).isEqualTo(9090)
    }

    @Test
    fun `hasDirectories false when empty`() {
        assertThat(ServerConfig().hasDirectories).isFalse()
    }

    @Test
    fun `hasDirectories true when at least one directory configured`() {
        val config = ServerConfig(
            sharedDirectories = mapOf(
                "movies" to SharedDirectory("movies", "Movies", "/storage/emulated/0/Movies")
            )
        )
        assertThat(config.hasDirectories).isTrue()
    }
}