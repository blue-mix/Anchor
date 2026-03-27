package com.example.anchor.domain.usecase.media

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.DirectoryListing
import com.example.anchor.domain.repository.MediaRepository
import java.io.File

/**
 * Use case for browsing a local directory.
 */
class BrowseMediaUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(baseDir: File, relativePath: String): Result<DirectoryListing> {
        return repository.browse(baseDir, relativePath)
    }
}