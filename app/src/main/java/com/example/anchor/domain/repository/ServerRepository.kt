package com.example.anchor.domain.repository

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.ServerConfig
import com.example.anchor.domain.model.ServerStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing the lifecycle and configuration of the HTTP server.
 *
 * The implementation ([ServerRepositoryImpl]) delegates to
 * [AnchorServerService] via Android service binding / Intents.
 * [DashboardViewModel] depends only on this interface.
 */
interface ServerRepository {

    /** Current server state — always up-to-date, backed by [AnchorServiceState]. */
    val status: StateFlow<ServerStatus>

    /**
     * Starts the server with [config].
     * @return [Result.Error] when the service cannot be started
     *         (e.g. missing Wi-Fi, port already in use).
     */
    suspend fun start(config: ServerConfig): Result<Unit>

    /** Stops the server. No-op when already stopped. */
    suspend fun stop(): Result<Unit>

    /**
     * Adds [directory] to a running server without restarting it.
     * @return [Result.Error] when the server is not running.
     */
    suspend fun addDirectory(alias: String, absolutePath: String): Result<Unit>

    /** Removes the directory with [alias] from the running server. */
    suspend fun removeDirectory(alias: String): Result<Unit>

    /** Returns true when the server is currently running. */
    val isRunning: Boolean
}