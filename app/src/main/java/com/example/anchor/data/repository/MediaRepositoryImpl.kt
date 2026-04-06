package com.example.anchor.data.repository

import com.example.anchor.core.result.Result
import com.example.anchor.data.source.local.FileSystemDataSource
import com.example.anchor.data.source.local.ThumbnailCache
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.model.MediaType
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Concrete implementation of [MediaRepository].
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
        val mediaType = MediaType.fromMimeType(
            com.example.anchor.core.util.MimeTypeUtils.getMimeType(file.name)
        )
        
        return when (mediaType) {
            MediaType.VIDEO -> thumbnailCache.getVideoThumbnail(file.absolutePath)
            MediaType.IMAGE -> thumbnailCache.getImageThumbnail(file.absolutePath)
            MediaType.AUDIO -> thumbnailCache.getAudioAlbumArt(file.absolutePath)
            else -> Result.Error("No thumbnail available for media type: $mediaType")
        }
    }

    override suspend fun getDirectoryStats(
        directory: File
    ): Result<MediaRepository.DirectoryStats> =
        fileSystemDataSource.getDirectoryStats(directory)
}
