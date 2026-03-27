package com.example.anchor.data.repository

import com.example.anchor.core.result.Result
import com.example.anchor.core.util.MimeTypeUtils
import com.example.anchor.data.source.local.FileSystemDataSource
import com.example.anchor.data.source.local.ThumbnailCache
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Concrete implementation of [MediaRepository].
 *
 * Delegates filesystem I/O to [FileSystemDataSource] and thumbnail
 * generation/caching to [ThumbnailCache].  Has no direct Android framework
 * imports of its own — all platform work lives in the data sources.
 */
class MediaRepositoryImpl(
    private val fileSystemDataSource: FileSystemDataSource,
    private val thumbnailCache: ThumbnailCache
) : MediaRepository {

    override suspend fun browse(
        baseDir: File,
        relativePath: String
    ): Result<DirectoryListing> =
        fileSystemDataSource.browse(baseDir, baseDir.name, relativePath)

    override suspend fun getFile(
        baseDir: File,
        relativePath: String
    ): Result<MediaItem> =
        fileSystemDataSource.getFile(baseDir, baseDir.name, relativePath)

    override suspend fun getThumbnail(file: File): Result<ByteArray> {
        val mimeType = MimeTypeUtils.getMimeType(file.name)
        return when {
            MimeTypeUtils.isVideo(mimeType) ->
                thumbnailCache.getVideoThumbnail(file.absolutePath)

            MimeTypeUtils.isImage(mimeType) ->
                thumbnailCache.getImageThumbnail(file.absolutePath)

            MimeTypeUtils.isAudio(mimeType) ->
                thumbnailCache.getAudioAlbumArt(file.absolutePath)

            else ->
                Result.Error("No thumbnail available for mime type: $mimeType")
        }
    }

    override suspend fun getDirectoryStats(
        directory: File
    ): Result<MediaRepository.DirectoryStats> =
        fileSystemDataSource.getDirectoryStats(directory)
}