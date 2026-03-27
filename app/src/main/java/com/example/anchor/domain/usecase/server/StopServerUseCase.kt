package com.example.anchor.domain.usecase.server

import com.example.anchor.core.result.Result
import com.example.anchor.domain.repository.ServerRepository

/**
 * Use case for stopping the Anchor HTTP server.
 */
class StopServerUseCase(private val repository: ServerRepository) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.stop()
    }
}