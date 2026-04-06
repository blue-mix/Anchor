package com.example.anchor.server

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentDirectoryServiceTest {

    @TempDir lateinit var tempDir: File

    private lateinit var service: ContentDirectoryService

    @BeforeEach
    fun setup() {
        service = ContentDirectoryService("http://192.168.1.5:8080")
    }

    // ── Root browse ───────────────────────────────────────────

    @Nested
    inner class BrowseRoot {

        @Test
        fun `empty directories returns empty DIDL result`() {
            service.updateSharedDirectories(emptyMap())
            val result = service.handleBrowse("0", "BrowseDirectChildren", "*", 0, 0, "")
            assertThat(result.totalMatches).isEqualTo(0)
            assertThat(result.numberReturned).isEqualTo(0)
            assertThat(result.result).contains("DIDL-Lite")
        }

        @Test
        fun `one directory creates one container in DIDL`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            File(moviesDir, "film.mp4").createNewFile()

            service.updateSharedDirectories(mapOf("movies" to moviesDir))
            val result = service.handleBrowse("0", "BrowseDirectChildren", "*", 0, 0, "")

            assertThat(result.totalMatches).isEqualTo(1)
            assertThat(result.numberReturned).isEqualTo(1)
            assertThat(result.result).contains("<container")
            assertThat(result.result).contains("Movies")
        }

        @Test
        fun `two directories returns two containers`() {
            val d1 = File(tempDir, "Movies").also { it.mkdir() }
            val d2 = File(tempDir, "Music").also { it.mkdir() }
            service.updateSharedDirectories(mapOf("movies" to d1, "music" to d2))

            val result = service.handleBrowse("0", "BrowseDirectChildren", "*", 0, 0, "")
            assertThat(result.totalMatches).isEqualTo(2)
        }

        @Test
        fun `pagination startingIndex and requestedCount respected`() {
            (1..5).forEach { i ->
                File(tempDir, "Dir$i").mkdir()
            }
            val dirs = (1..5).associate { "dir$it" to File(tempDir, "Dir$it") }
            service.updateSharedDirectories(dirs)

            val page1 = service.handleBrowse("0", "BrowseDirectChildren", "*", 0, 2, "")
            assertThat(page1.numberReturned).isEqualTo(2)
            assertThat(page1.totalMatches).isEqualTo(5)

            val page2 = service.handleBrowse("0", "BrowseDirectChildren", "*", 2, 2, "")
            assertThat(page2.numberReturned).isEqualTo(2)
        }
    }

    // ── Directory browse ──────────────────────────────────────

    @Nested
    inner class BrowseDirectory {

        @Test
        fun `video file produces item with correct upnp class`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            File(moviesDir, "film.mp4").createNewFile()
            service.updateSharedDirectories(mapOf("movies" to moviesDir))

            val result = service.handleBrowse(
                "dir:movies", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("object.item.videoItem")
            assertThat(result.result).contains("film.mp4")
        }

        @Test
        fun `audio file produces audioItem upnp class`() {
            val musicDir = File(tempDir, "Music").also { it.mkdir() }
            File(musicDir, "song.mp3").createNewFile()
            service.updateSharedDirectories(mapOf("music" to musicDir))

            val result = service.handleBrowse(
                "dir:music", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("object.item.audioItem")
        }

        @Test
        fun `image file produces imageItem upnp class`() {
            val picDir = File(tempDir, "Pics").also { it.mkdir() }
            File(picDir, "photo.jpg").createNewFile()
            service.updateSharedDirectories(mapOf("pics" to picDir))

            val result = service.handleBrowse(
                "dir:pics", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("object.item.imageItem")
        }

        @Test
        fun `subdirectory appears as container`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            File(moviesDir, "Action").mkdir()
            service.updateSharedDirectories(mapOf("movies" to moviesDir))

            val result = service.handleBrowse(
                "dir:movies", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("<container")
            assertThat(result.result).contains("Action")
        }

        @Test
        fun `unknown alias returns empty result`() {
            service.updateSharedDirectories(mapOf("movies" to File(tempDir, "Movies").also { it.mkdir() }))
            val result = service.handleBrowse(
                "dir:nonexistent", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.numberReturned).isEqualTo(0)
            assertThat(result.totalMatches).isEqualTo(0)
        }

        @Test
        fun `file URL contains serverBaseUrl and alias`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            File(moviesDir, "film.mp4").createNewFile()
            service.updateSharedDirectories(mapOf("movies" to moviesDir))

            val result = service.handleBrowse(
                "dir:movies", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("http://192.168.1.5:8080/files/movies")
        }

        @Test
        fun `hidden files are excluded`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            File(moviesDir, "film.mp4").createNewFile()
            File(moviesDir, ".hidden").createNewFile()
            service.updateSharedDirectories(mapOf("movies" to moviesDir))

            val result = service.handleBrowse(
                "dir:movies", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.totalMatches).isEqualTo(1)
            assertThat(result.result).doesNotContain(".hidden")
        }

        @Test
        fun `nested browse works via alias slash path`() {
            val moviesDir = File(tempDir, "Movies").also { it.mkdir() }
            val actionDir = File(moviesDir, "Action").also { it.mkdir() }
            File(actionDir, "hero.mp4").createNewFile()
            service.updateSharedDirectories(mapOf("movies" to moviesDir))

            val result = service.handleBrowse(
                "dir:movies/Action", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.totalMatches).isEqualTo(1)
            assertThat(result.result).contains("hero")
        }
    }

    // ── systemUpdateId ────────────────────────────────────────

    @Nested
    inner class SystemUpdateId {

        @Test
        fun `increments on updateSharedDirectories`() {
            val initial = service.getSystemUpdateId()
            service.updateSharedDirectories(emptyMap())
            assertThat(service.getSystemUpdateId()).isGreaterThan(initial)
        }

        @Test
        fun `incrementUpdateId adds one`() {
            val before = service.getSystemUpdateId()
            service.incrementUpdateId()
            assertThat(service.getSystemUpdateId()).isEqualTo(before + 1)
        }
    }

    // ── DIDL-Lite structure ───────────────────────────────────

    @Nested
    inner class DidlStructure {

        @Test
        fun `result is wrapped in DIDL-Lite with correct namespaces`() {
            service.updateSharedDirectories(emptyMap())
            val result = service.handleBrowse("0", "BrowseDirectChildren", "*", 0, 0, "")
            assertThat(result.result).contains("urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/")
            assertThat(result.result).contains("xmlns:dc=")
            assertThat(result.result).contains("xmlns:upnp=")
        }

        @Test
        fun `special characters in file name are XML-escaped`() {
            val musicDir = File(tempDir, "Music").also { it.mkdir() }
            File(musicDir, "Song and Dance.mp3").createNewFile()
            service.updateSharedDirectories(mapOf("music" to musicDir))

            val result = service.handleBrowse(
                "dir:music", "BrowseDirectChildren", "*", 0, 0, ""
            )
            assertThat(result.result).contains("Song and Dance")
        }
    }
}