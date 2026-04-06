package com.example.anchor.extensions

import com.example.anchor.core.extension.ensureDirectories
import com.example.anchor.core.extension.extension
import com.example.anchor.core.extension.isDescendantOf
import com.example.anchor.core.extension.nameWithoutExtension
import com.example.anchor.core.extension.shallowFileCount
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileExtensionsTest {

    @TempDir
    lateinit var tempDir: File

    // ── formattedSize ─────────────────────────────────────────

    @Test
    fun `formats bytes correctly`() {
        assertThat(512L.formattedSize()).isEqualTo("512 B")
    }

    @Test
    fun `formats kilobytes correctly`() {
        assertThat(2048L.formattedSize()).isEqualTo("2 KB")
    }

    @Test
    fun `formats megabytes correctly`() {
        assertThat((5L * 1_048_576L).formattedSize()).isEqualTo("5 MB")
    }

    @Test
    fun `formats gigabytes with two decimal places`() {
        val twoGb = 2L * 1_073_741_824L
        assertThat(twoGb.formattedSize()).isEqualTo("2 GB")
    }

    // ── extension ─────────────────────────────────────────────

    @Test
    fun `extension returns lowercase extension`() {
        assertThat(File(tempDir, "video.MP4").extension).isEqualTo("mp4")
    }

    @Test
    fun `extension returns empty string for no extension`() {
        assertThat(File(tempDir, "README").extension).isEqualTo("")
    }

    @Test
    fun `extension handles multiple dots correctly`() {
        assertThat(File(tempDir, "backup.tar.gz").extension).isEqualTo("gz")
    }

    // ── nameWithoutExtension ──────────────────────────────────

    @Test
    fun `nameWithoutExtension strips extension`() {
        assertThat(File(tempDir, "movie.mkv").nameWithoutExtension).isEqualTo("movie")
    }

    @Test
    fun `nameWithoutExtension returns full name when no extension`() {
        assertThat(File(tempDir, "README").nameWithoutExtension).isEqualTo("README")
    }

    // ── isDescendantOf ────────────────────────────────────────

    @Test
    fun `isDescendantOf returns true for child`() {
        val child = File(tempDir, "sub/file.txt")
        assertThat(child.isDescendantOf(tempDir)).isTrue()
    }

    @Test
    fun `isDescendantOf returns true for self`() {
        assertThat(tempDir.isDescendantOf(tempDir)).isTrue()
    }

    @Test
    fun `isDescendantOf returns false for path traversal`() {
        val outside = File(tempDir, "../outside.txt")
        assertThat(outside.isDescendantOf(tempDir)).isFalse()
    }

    // ── shallowFileCount ──────────────────────────────────────

    @Test
    fun `shallowFileCount counts non-hidden files`() {
        File(tempDir, "a.mp4").createNewFile()
        File(tempDir, "b.mp3").createNewFile()
        File(tempDir, "subdir").mkdir()

        assertThat(tempDir.shallowFileCount()).isEqualTo(2)
    }

    // ── ensureDirectories ─────────────────────────────────────

    @Test
    fun `ensureDirectories creates missing parents`() {
        val deep = File(tempDir, "a/b/c")
        deep.ensureDirectories()
        assertThat(deep.isDirectory).isTrue()
    }
}