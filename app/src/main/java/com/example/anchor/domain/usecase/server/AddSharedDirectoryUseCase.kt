package com.example.anchor.domain.usecase.server

import com.example.anchor.core.result.Result
import com.example.anchor.domain.repository.ServerRepository

/**
 * Adds a new shared directory to the running server.
 */
class AddSharedDirectoryUseCase(private val repository: ServerRepository) {
    suspend operator fun invoke(alias: String, absolutePath: String): Result<Unit> {
        return repository.addDirectory(alias, absolutePath)
    }
}