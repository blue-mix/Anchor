package com.example.anchor.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MimeTypeUtilsTest {

    // ── getMimeType ───────────────────────────────────────────

    @Nested
    inner class GetMimeType {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(
            // Video
            "video.mp4,    video/mp4",
            "film.mkv,     video/x-matroska",
            "clip.avi,     video/x-msvideo",
            "movie.mov,    video/quicktime",
            "stream.ts,    video/mp2t",
            "reel.webm,    video/webm",
            // Audio
            "song.mp3,     audio/mpeg",
            "track.flac,   audio/flac",
            "sound.wav,    audio/wav",
            "beat.aac,     audio/aac",
            "music.ogg,    audio/ogg",
            "lossless.m4a, audio/mp4",
            "voice.opus,   audio/opus",
            // Image
            "photo.jpg,    image/jpeg",
            "img.jpeg,     image/jpeg",
            "graphic.png,  image/png",
            "anim.gif,     image/gif",
            "modern.webp,  image/webp",
            "raw.heic,     image/heic",
            // Document
            "doc.pdf,      application/pdf",
            "notes.txt,    text/plain",
            "page.html,    text/html"
        )
        fun `returns correct mime type for known extensions`(
            fileName: String,
            expected: String
        ) {
            assertThat(MimeTypeUtils.getMimeType(fileName.trim()))
                .isEqualTo(expected.trim())
        }

        @Test
        fun `extension matching is case-insensitive`() {
            assertThat(MimeTypeUtils.getMimeType("VIDEO.MP4")).isEqualTo("video/mp4")
            assertThat(MimeTypeUtils.getMimeType("Photo.JPG")).isEqualTo("image/jpeg")
            assertThat(MimeTypeUtils.getMimeType("Track.FLAC")).isEqualTo("audio/flac")
        }

        @Test
        fun `returns octet-stream for unknown extension`() {
            assertThat(MimeTypeUtils.getMimeType("archive.xyz"))
                .isEqualTo("application/octet-stream")
        }

        @Test
        fun `returns octet-stream for file with no extension`() {
            assertThat(MimeTypeUtils.getMimeType("README"))
                .isEqualTo("application/octet-stream")
        }

        @Test
        fun `returns octet-stream for empty string`() {
            assertThat(MimeTypeUtils.getMimeType(""))
                .isEqualTo("application/octet-stream")
        }

        @Test
        fun `handles file name with multiple dots`() {
            assertThat(MimeTypeUtils.getMimeType("my.backup.tar.gz"))
                .isEqualTo("application/gzip")
        }
    }

    // ── Type check predicates ─────────────────────────────────

    @Nested
    inner class TypeChecks {

        @Test
        fun `isVideo true for all video subtypes`() {
            assertThat(MimeTypeUtils.isVideo("video/mp4")).isTrue()
            assertThat(MimeTypeUtils.isVideo("video/x-matroska")).isTrue()
            assertThat(MimeTypeUtils.isVideo("video/webm")).isTrue()
        }

        @Test
        fun `isVideo false for non-video types`() {
            assertThat(MimeTypeUtils.isVideo("audio/mpeg")).isFalse()
            assertThat(MimeTypeUtils.isVideo("image/jpeg")).isFalse()
        }

        @Test
        fun `isAudio true for audio subtypes`() {
            assertThat(MimeTypeUtils.isAudio("audio/mpeg")).isTrue()
            assertThat(MimeTypeUtils.isAudio("audio/flac")).isTrue()
        }

        @Test
        fun `isAudio false for video`() {
            assertThat(MimeTypeUtils.isAudio("video/mp4")).isFalse()
        }

        @Test
        fun `isImage true for image subtypes`() {
            assertThat(MimeTypeUtils.isImage("image/jpeg")).isTrue()
            assertThat(MimeTypeUtils.isImage("image/png")).isTrue()
            assertThat(MimeTypeUtils.isImage("image/webp")).isTrue()
        }

        @Test
        fun `isStreamable true for video and audio, false for image`() {
            assertThat(MimeTypeUtils.isStreamable("video/mp4")).isTrue()
            assertThat(MimeTypeUtils.isStreamable("audio/flac")).isTrue()
            assertThat(MimeTypeUtils.isStreamable("image/png")).isFalse()
            assertThat(MimeTypeUtils.isStreamable("application/pdf")).isFalse()
        }

        @Test
        fun `isMedia true for video audio image`() {
            assertThat(MimeTypeUtils.isMedia("video/mp4")).isTrue()
            assertThat(MimeTypeUtils.isMedia("audio/mpeg")).isTrue()
            assertThat(MimeTypeUtils.isMedia("image/jpeg")).isTrue()
            assertThat(MimeTypeUtils.isMedia("application/pdf")).isFalse()
        }

        @Test
        fun `isDocument true for pdf and text types`() {
            assertThat(MimeTypeUtils.isDocument("application/pdf")).isTrue()
            assertThat(MimeTypeUtils.isDocument("text/plain")).isTrue()
            assertThat(MimeTypeUtils.isDocument("text/html")).isTrue()
            assertThat(MimeTypeUtils.isDocument("video/mp4")).isFalse()
        }
    }

    // ── getExtensionFromMimeType ──────────────────────────────

    @Nested
    inner class GetExtension {

        @Test
        fun `returns extension for known mime type`() {
            assertThat(MimeTypeUtils.getExtensionFromMimeType("video/mp4")).isEqualTo("mp4")
            assertThat(MimeTypeUtils.getExtensionFromMimeType("audio/mpeg")).isEqualTo("mp3")
            assertThat(MimeTypeUtils.getExtensionFromMimeType("image/jpeg")).isEqualTo("jpg")
        }

        @Test
        fun `returns null for unknown mime type`() {
            assertThat(MimeTypeUtils.getExtensionFromMimeType("application/unknown-xyz"))
                .isNull()
        }
    }

    // ── getLabel ──────────────────────────────────────────────

    @Nested
    inner class GetLabel {

        @Test
        fun `returns uppercase extension label`() {
            assertThat(MimeTypeUtils.getLabel("video/mp4")).isEqualTo("MP4")
            assertThat(MimeTypeUtils.getLabel("audio/flac")).isEqualTo("FLAC")
        }
    }
}