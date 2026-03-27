package com.example.anchor.data.source

import com.example.anchor.data.source.local.FileSystemDataSource
import com.example.anchor.core.result.Result
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileSystemDataSourceTest {

    private lateinit var dataSource: FileSystemDataSource

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        dataSource = FileSystemDataSource()
    }

    // ── browse ────────────────────────────────────────────────

    @Nested
    inner class Browse {

        @Test
        fun `returns listing for empty directory`() = runTest {
            val result = dataSource.browse(tempDir, "media", "")
            assertThat(result.isSuccess).isTrue()
            val listing = (result as Result.Success).data
            assertThat(listing.items).isEmpty()
            assertThat(listing.path).isEqualTo("/media")
        }

        @Test
        fun `returns files in directory`() = runTest {
            File(tempDir, "a.mp4").createNewFile()
            File(tempDir, "b.mp3").createNewFile()

            val listing = (dataSource.browse(tempDir, "media", "") as Result.Success).data
            assertThat(listing.items).hasSize(2)
            assertThat(listing.items.map { it.name }).containsExactly("a.mp4", "b.mp3")
        }

        @Test
        fun `sorts directories before files`() = runTest {
            File(tempDir, "z_file.mp4").createNewFile()
            File(tempDir, "a_folder").mkdir()

            val listing = (dataSource.browse(tempDir, "media", "") as Result.Success).data
            assertThat(listing.items.first().isDirectory).isTrue()
        }

        @Test
        fun `excludes hidden files`() = runTest {
            File(tempDir, "visible.mp4").createNewFile()
            File(tempDir, ".hidden_file").createNewFile()

            val listing = (dataSource.browse(tempDir, "media", "") as Result.Success).data
            assertThat(listing.items.map { it.name }).doesNotContain(".hidden_file")
        }

        @Test
        fun `browses subdirectory correctly`() = runTest {
            val sub = File(tempDir, "Action").also { it.mkdir() }
            File(sub, "film.mkv").createNewFile()

            val listing = (dataSource.browse(tempDir, "movies", "Action") as Result.Success).data
            assertThat(listing.items).hasSize(1)
            assertThat(listing.items.first().name).isEqualTo("film.mkv")
        }

        @Test
        fun `returns error for non-existent path`() = runTest {
            val result = dataSource.browse(tempDir, "media", "nonexistent")
            assertThat(result.isError).isTrue()
        }

        @Test
        fun `returns error when path is a file not a directory`() = runTest {
            File(tempDir, "file.mp4").createNewFile()
            val result = dataSource.browse(tempDir, "media", "file.mp4")
            assertThat(result.isError).isTrue()
        }

        @Test
        fun `parentPath is null at root`() = runTest {
            val listing = (dataSource.browse(tempDir, "movies", "") as Result.Success).data
            assertThat(listing.parentPath).isNull()
        }

        @Test
        fun `parentPath points to alias root for direct child dir`() = runTest {
            val sub = File(tempDir, "Action").also { it.mkdir() }
            val listing = (dataSource.browse(tempDir, "movies", "Action") as Result.Success).data
            assertThat(listing.parentPath).isEqualTo("/movies")
        }
    }

    // ── resolveAndValidate (security) ─────────────────────────

    @Nested
    inner class PathTraversal {

        @Test
        fun `blocks traversal with double-dot segments`() = runTest {
            val result = dataSource.browse(tempDir, "media", "../../etc/passwd")
            assertThat(result.isError).isTrue()
            val error = (result as Result.Error).message
            assertThat(error).ignoringCase().contains("access denied")
        }

        @Test
        fun `blocks traversal via encoded double-dots`() = runTest {
            // After URL decoding this becomes ../../
            val result = dataSource.browse(tempDir, "media", "%2e%2e/%2e%2e/etc")
            // Either not found or access denied — both are acceptable
            assertThat(result.isError).isTrue()
        }

        @Test
        fun `allows deep legitimate path`() = runTest {
            val deep = File(tempDir, "a/b/c").also { it.mkdirs() }
            File(deep, "clip.mp4").createNewFile()

            val result = dataSource.browse(tempDir, "media", "a/b/c")
            assertThat(result.isSuccess).isTrue()
        }

        @Test
        fun `direct resolve of base dir itself is allowed`() {
            val resolved = dataSource.resolveAndValidate(tempDir, "")
            assertThat(resolved.canonicalPath).isEqualTo(tempDir.canonicalPath)
        }
    }

    // ── getFile ───────────────────────────────────────────────

    @Nested
    inner class GetFile {

        @Test
        fun `returns MediaItem for existing file`() = runTest {
            File(tempDir, "track.flac").createNewFile()

            val result = dataSource.getFile(tempDir, "music", "track.flac")
            assertThat(result.isSuccess).isTrue()
            val item = (result as Result.Success).data
            assertThat(item.name).isEqualTo("track.flac")
            assertThat(item.mimeType).isEqualTo("audio/flac")
            assertThat(item.isDirectory).isFalse()
        }

        @Test
        fun `returns error for missing file`() = runTest {
            val result = dataSource.getFile(tempDir, "music", "ghost.mp3")
            assertThat(result.isError).isTrue()
        }

        @Test
        fun `returns directory as MediaItem with isDirectory=true`() = runTest {
            File(tempDir, "Album").mkdir()
            val result = dataSource.getFile(tempDir, "music", "Album")
            assertThat((result as Result.Success).data.isDirectory).isTrue()
        }
    }

    // ── getDirectoryStats ─────────────────────────────────────

    @Nested
    inner class GetDirectoryStats {

        @Test
        fun `counts files recursively`() = runTest {
            File(tempDir, "a.mp4").writeBytes(ByteArray(1_000))
            val sub = File(tempDir, "sub").also { it.mkdir() }
            File(sub, "b.mp3").writeBytes(ByteArray(2_000))

            val stats = (dataSource.getDirectoryStats(tempDir) as Result.Success).data
            assertThat(stats.fileCount).isEqualTo(2)
            assertThat(stats.totalSize).isEqualTo(3_000L)
        }

        @Test
        fun `returns zero for empty directory`() = runTest {
            val stats = (dataSource.getDirectoryStats(tempDir) as Result.Success).data
            assertThat(stats.fileCount).isEqualTo(0)
            assertThat(stats.totalSize).isEqualTo(0L)
        }

        @Test
        fun `does not count directories themselves`() = runTest {
            File(tempDir, "subdir").mkdir()
            File(tempDir, "file.mp4").writeBytes(ByteArray(500))

            val stats = (dataSource.getDirectoryStats(tempDir) as Result.Success).data
            assertThat(stats.fileCount).isEqualTo(1)
        }
    }
}