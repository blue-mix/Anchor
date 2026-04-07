package com.example.anchor.data.repository

import android.content.Context
import android.util.Log
import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf
import com.example.anchor.domain.model.ServerConfig
import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.domain.repository.ServerRepository
import com.example.anchor.data.server.service.AnchorServerService
import com.example.anchor.data.server.service.AnchorServiceState
import kotlinx.coroutines.flow.StateFlow

/**
 * Concrete implementation of [ServerRepository].
 *
 * Bridges the domain layer to [AnchorServerService] via Intents and
 * [AnchorServiceState] — the singleton state bus shared between the
 * Service and the UI.
 *
 * The old implementation manually mapped the flat ServerState data class
 * to the domain ServerStatus sealed interface through an anonymous
 * StateFlow wrapper.  That mapping is now unnecessary because
 * [AnchorServiceState.status] already emits [ServerStatus] directly —
 * so this class just delegates straight through.
 *
 * Note: Android's foreground service model means we cannot return a
 * meaningful [Result.Error] from start/stop synchronously — errors surface
 * through [AnchorServiceState.status] as [ServerStatus.Error].
 * The [Result] return values here only confirm that the Intent was
 * successfully dispatched to the service.
 */
class ServerRepositoryImpl(
    private val context: Context
) : ServerRepository {

    companion object {
        private const val TAG = "ServerRepositoryImpl"
    }

    // ── Status ────────────────────────────────────────────────

    /**
     * Delegates directly to [AnchorServiceState.status] which already
     * emits domain [ServerStatus] values — no mapping layer needed.
     */
    override val status: StateFlow<ServerStatus>
        get() = AnchorServiceState.status

    override val isRunning: Boolean
        get() = AnchorServiceState.status.value is ServerStatus.Running

    // ── Lifecycle ─────────────────────────────────────────────

    override suspend fun start(config: ServerConfig): Result<Unit> = resultOf {
        val dirs = ArrayList(
            config.sharedDirectories.values.map { it.absolutePath }
        )
        AnchorServerService.startServer(
            context = context,
            port = config.port,
            directories = dirs
        )
        Log.d(TAG, "startServer dispatched — port=${config.port}, dirs=${dirs.size}")
    }

    override suspend fun stop(): Result<Unit> = resultOf {
        AnchorServerService.stopServer(context)
        Log.d(TAG, "stopServer dispatched")
    }

    override suspend fun addDirectory(
        alias: String,
        absolutePath: String
    ): Result<Unit> = resultOf {
        Log.d(TAG, "addDirectory: alias=$alias, path=$absolutePath")
        // TODO: bind to LocalBinder and call service.addDirectory(absolutePath)
    }

    override suspend fun removeDirectory(alias: String): Result<Unit> = resultOf {
        Log.d(TAG, "removeDirectory: alias=$alias")
        // TODO: bind to LocalBinder and call service.removeDirectory(alias)
    }
}