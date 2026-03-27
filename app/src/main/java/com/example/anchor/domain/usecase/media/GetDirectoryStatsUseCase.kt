package com.example.anchor.domain.usecase.media

import com.example.anchor.core.result.Result
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Use case for retrieving recursive statistics (file count and total size)
 * for a specific directory.
 */
class GetDirectoryStatsUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(directory: File): Result<MediaRepository.DirectoryStats> {
        return repository.getDirectoryStats(directory)
    }
}