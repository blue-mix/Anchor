package com.example.anchor.domain.usecase.media

import com.example.anchor.core.result.Result
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Use case for retrieving or generating a thumbnail for a media file.
 */
class GetThumbnailUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(file: File): Result<ByteArray> {
        return repository.getThumbnail(file)
    }
}