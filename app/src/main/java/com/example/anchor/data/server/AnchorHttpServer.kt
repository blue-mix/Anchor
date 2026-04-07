package com.example.anchor.data.server

import android.util.Log
import com.example.anchor.data.server.service.AnchorServiceState
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import java.io.File

class AnchorHttpServer(
    private val port: Int,
    private val directoryManager: SharedDirectoryManager,
    private val dlnaManager: DlnaManager,
    private val routeProvider: RouteProvider
) {
    companion object {
        private const val TAG = "AnchorHttpServer"
    }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    // ── Shared directory management ──────────────────────────

    fun addSharedDirectory(alias: String, directory: File) {
        if (directoryManager.add(alias, directory)) {
            dlnaManager.syncDirectories()
        }
    }

    fun removeSharedDirectory(alias: String) {
        directoryManager.remove(alias)
        dlnaManager.syncDirectories()
    }

    fun clearSharedDirectories() {
        directoryManager.clear()
        dlnaManager.syncDirectories()
    }

    // ── Lifecycle ─────────────────────────────────────────────

    fun start() {
        if (server != null) {
            Log.w(TAG, "Server already running")
            return
        }

        dlnaManager.start(port)
        
        server = embeddedServer(Netty, port = port) {
            routeProvider.configureRoutes(this)
        }.start(wait = false)

        Log.d(TAG, "Server started on port $port")
        AnchorServiceState.addLog("HTTP server started on port $port")
    }

    fun stop() {
        dlnaManager.stop()
        server?.stop(1000, 2000)
        server = null

        Log.d(TAG, "Server stopped")
        AnchorServiceState.addLog("HTTP server stopped")
    }

    fun isRunning(): Boolean = server != null
}
