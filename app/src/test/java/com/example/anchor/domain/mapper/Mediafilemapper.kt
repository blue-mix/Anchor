package com.example.anchor.data.mapper

import com.example.anchor.data.dto.DirectoryListingDto
import com.example.anchor.data.dto.MediaFileDto
import com.example.anchor.data.mapper.MediaFileMapper.toDomain
import com.example.anchor.data.mapper.MediaFileMapper.toDto
import com.example.anchor.data.mapper.MediaFileMapper.filesToDirectoryListing
import com.example.anchor.data.mapper.encodePath
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MediaFileMapperTest {

    @TempDir
    lateinit var tempDir: File

    // ── DTO → Domain ──────────────────────────────────────────

    @Nested
    inner class DtoToDomain {

        @Test
        fun `maps all fields from dto correctly`() {
            val dto = MediaFileDto(
                name         = "movie.mp4",
                path         = "/movies/movie.mp4",
                absolutePath = "/storage/emulated/0/Movies/movie.mp4",
                isDirectory  = false,
                size         = 1_048_576L,
                mimeType     = "video/mp4",
                lastModified = 1_700_000_000L,
                mediaType    = "VIDEO"
            )

            val item = dto.toDomain()

            assertThat(item.name).isEqualTo("movie.mp4")
            assertThat(item.path).isEqualTo("/movies/movie.mp4")
            assertThat(item.size).isEqualTo(1_048_576L)
            assertThat(item.mimeType).isEqualTo("video/mp4")
            assertThat(item.mediaType).isEqualTo(MediaType.VIDEO)
            assertThat(item.isDirectory).isFalse()
        }

        @Test
        fun `handles unknown media type string gracefully`() {
            val dto = MediaFileDto(
                name      = "file.xyz",
                path      = "/misc/file.xyz",
                mediaType = "TOTALLY_UNKNOWN_VALUE"
            )
            val item = dto.toDomain()
            assertThat(item.mediaType).isEqualTo(MediaType.UNKNOWN)
        }

        @Test
        fun `maps directory dto correctly`() {
            val dto = MediaFileDto(
                name        = "Action",
                path        = "/movies/Action",
                isDirectory = true,
                mediaType   = "UNKNOWN"
            )
            val item = dto.toDomain()
            assertThat(item.isDirectory).isTrue()
            assertThat(item.mediaType).isEqualTo(MediaType.UNKNOWN)
        }

        @Test
        fun `maps DirectoryListingDto to domain`() {
            val listingDto = DirectoryListingDto(
                path       = "/movies",
                parentPath = null,
                files      = listOf(
                    MediaFileDto("a.mp4", "/movies/a.mp4", mediaType = "VIDEO"),
                    MediaFileDto("b.mp3", "/movies/b.mp3", mediaType = "AUDIO")
                ),
                totalFiles = 2,
                totalSize  = 2_000L
            )

            val listing = listingDto.toDomain()

            assertThat(listing.path).isEqualTo("/movies")
            assertThat(listing.parentPath).isNull()
            assertThat(listing.items).hasSize(2)
            assertThat(listing.totalItems).isEqualTo(2)
            assertThat(listing.totalSize).isEqualTo(2_000L)
        }
    }

    // ── File → Domain (local) ─────────────────────────────────

    @Nested
    inner class FileToDomain {

        @Test
        fun `maps regular file with correct mime and media type`() {
            val file = File(tempDir, "song.mp3").also { it.createNewFile() }

            val item = file.toDomain(tempDir, "music")

            assertThat(item.name).isEqualTo("song.mp3")
            assertThat(item.mimeType).isEqualTo("audio/mpeg")
            assertThat(item.mediaType).isEqualTo(MediaType.AUDIO)
            assertThat(item.isDirectory).isFalse()
            assertThat(item.path).isEqualTo("/music/song.mp3")
            assertThat(item.absolutePath).isEqualTo(file.absolutePath)
        }

        @Test
        fun `maps directory with UNKNOWN media type and empty mimeType`() {
            val dir = File(tempDir, "Action").also { it.mkdir() }

            val item = dir.toDomain(tempDir, "movies")

            assertThat(item.isDirectory).isTrue()
            assertThat(item.mimeType).isEmpty()
            assertThat(item.mediaType).isEqualTo(MediaType.UNKNOWN)
            assertThat(item.size).isEqualTo(0L)
        }

        @Test
        fun `builds correct nested path`() {
            val sub  = File(tempDir, "Sub").also { it.mkdir() }
            val file = File(sub, "clip.mp4").also { it.createNewFile() }

            val item = file.toDomain(tempDir, "videos")

            assertThat(item.path).isEqualTo("/videos/Sub/clip.mp4")
        }

        @Test
        fun `baseDir maps to alias root path`() {
            val item = tempDir.toDomain(tempDir, "root")
            assertThat(item.path).isEqualTo("/root")
        }
    }

    // ── filesToDirectoryListing ───────────────────────────────

    @Nested
    inner class DirectoryListingMapping {

        @Test
        fun `sorts directories before files`() {
            File(tempDir, "z_video.mp4").createNewFile()
            File(tempDir, "a_subdir").mkdir()
            File(tempDir, "m_song.mp3").createNewFile()

            val listing = filesToDirectoryListing(tempDir, "media", tempDir)

            assertThat(listing.items.first().isDirectory).isTrue()
            assertThat(listing.items.first().name).isEqualTo("a_subdir")
        }

        @Test
        fun `excludes hidden files`() {
            File(tempDir, "visible.mp4").createNewFile()
            // Hidden files start with '.' on Unix
            File(tempDir, ".hidden").createNewFile()

            val listing = filesToDirectoryListing(tempDir, "media", tempDir)

            assertThat(listing.items.map { it.name }).doesNotContain(".hidden")
        }

        @Test
        fun `parentPath is null at root`() {
            val listing = filesToDirectoryListing(tempDir, "movies", tempDir)
            assertThat(listing.parentPath).isNull()
        }

        @Test
        fun `parentPath points to alias root for direct child`() {
            val sub = File(tempDir, "Action").also { it.mkdir() }
            val listing = filesToDirectoryListing(tempDir, "movies", sub)
            assertThat(listing.parentPath).isEqualTo("/movies")
        }

        @Test
        fun `parentPath is correct for deeply nested directory`() {
            val sub = File(tempDir, "Action/2024").also { it.mkdirs() }
            val listing = filesToDirectoryListing(tempDir, "movies", sub)
            assertThat(listing.parentPath).isEqualTo("/movies/Action")
        }

        @Test
        fun `totalSize sums file sizes correctly`() {
            val f1 = File(tempDir, "a.mp4").also { it.writeBytes(ByteArray(1_000)) }
            val f2 = File(tempDir, "b.mp4").also { it.writeBytes(ByteArray(2_000)) }

            val listing = filesToDirectoryListing(tempDir, "movies", tempDir)

            assertThat(listing.totalSize).isEqualTo(3_000L)
        }

        @Test
        fun `does not include directory size in totalSize`() {
            val sub = File(tempDir, "subdir").also { it.mkdir() }
            File(tempDir, "file.mp4").writeBytes(ByteArray(500))

            val listing = filesToDirectoryListing(tempDir, "movies", tempDir)

            assertThat(listing.totalSize).isEqualTo(500L)
        }
    }

    // ── Domain → DTO (round-trip) ─────────────────────────────

    @Nested
    inner class DomainToDto {

        @Test
        fun `round-trip preserves all fields`() {
            val original = MediaItem(
                name         = "film.mkv",
                path         = "/movies/film.mkv",
                absolutePath = "/storage/emulated/0/Movies/film.mkv",
                isDirectory  = false,
                size         = 2_000_000L,
                mimeType     = "video/x-matroska",
                lastModified = 1_700_000_000L,
                mediaType    = MediaType.VIDEO
            )

            val dto    = original.toDto()
            val result = dto.toDomain()

            assertThat(result).isEqualTo(original)
        }
    }

    // ── encodePath ────────────────────────────────────────────

    @Nested
    inner class EncodePath {

        @Test
        fun `encodes spaces as %20`() {
            val encoded = encodePath("/movies/My Film.mp4")
            assertThat(encoded).contains("%20")
            assertThat(encoded).doesNotContain(" ")
        }

        @Test
        fun `preserves slashes between segments`() {
            val encoded = encodePath("/movies/Action/film.mp4")
            assertThat(encoded).isEqualTo("/movies/Action/film.mp4")
        }

        @Test
        fun `encodes special characters`() {
            val encoded = encodePath("/music/Ångström & Friends.mp3")
            assertThat(encoded).doesNotContain(" ")
            assertThat(encoded).doesNotContain("&")
        }
    }
}