package com.example.anchor.domain.usecase.server

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.ServerConfig
import com.example.anchor.domain.repository.ServerRepository

/**
 * Validates and starts the Anchor HTTP server.
 */
class StartServerUseCase(private val repository: ServerRepository) {
    suspend operator fun invoke(config: ServerConfig): Result<Unit> {
        // Here we could add domain validation, e.g. checking if any folders are shared
        return repository.start(config)
    }
}