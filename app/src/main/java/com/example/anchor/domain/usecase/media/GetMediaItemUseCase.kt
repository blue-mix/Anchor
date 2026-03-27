package com.example.anchor.domain.usecase.media

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Use case for retrieving metadata for a single media file.
 */
class GetMediaItemUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(baseDir: File, relativePath: String): Result<MediaItem> {
        return repository.getFile(baseDir, relativePath)
    }
}