package com.example.anchor.server.service

import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.server.service.AnchorServiceState.logs
import com.example.anchor.server.service.AnchorServiceState.status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Log types ─────────────────────────────────────────────────

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

/**
 * Singleton state bus shared between [AnchorServerService] (writer)
 * and the UI / repositories (readers).
 *
 * Now uses the domain [ServerStatus] sealed interface instead of the
 * old flat [ServerState] data class, so ViewModels consume domain types
 * directly without an extra mapping step.
 *
 * Thread-safety: [MutableStateFlow] updates are atomic; callers on any
 * thread can safely read [status] and [logs].
 */
object AnchorServiceState {

    // ── Status ────────────────────────────────────────────────

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    // ── Logs ──────────────────────────────────────────────────

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // ── Status mutators ───────────────────────────────────────

    fun setStarting() {
        _status.value = ServerStatus.Starting
    }

    fun setRunning(ipAddress: String, port: Int) {
        _status.value = ServerStatus.Running(ipAddress, port)
        addLog("Server running at http://$ipAddress:$port")
    }

    fun setStopped() {
        _status.value = ServerStatus.Stopped
    }

    fun setError(message: String) {
        _status.value = ServerStatus.Error(message)
        addLog(message, LogLevel.ERROR)
    }

    // ── Convenience shims for call-sites that still pass a Boolean ─

    /**
     * Called by [AnchorServerService] after startup sequence completes.
     * Replaces the old setRunning(running: Boolean, ip, port) overload.
     */
    fun setRunning(running: Boolean, ip: String? = null, port: Int = 8080) {
        if (running && ip != null) setRunning(ip, port)
        else if (!running) setStopped()
    }

    // ── Log mutators ──────────────────────────────────────────

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            message = message,
            level = level
        )
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // ── Shared directories (kept for backward compat with service) ──

    private val _sharedDirectories = MutableStateFlow<List<String>>(emptyList())
    val sharedDirectories: StateFlow<List<String>> = _sharedDirectories.asStateFlow()

    fun setSharedDirectories(dirs: List<String>) {
        _sharedDirectories.value = dirs
    }

    // ── Reset ─────────────────────────────────────────────────

    /** Called from [AnchorServerService.onDestroy]. */
    fun reset() {
        _status.value = ServerStatus.Stopped
        _logs.value = emptyList()
        _sharedDirectories.value = emptyList()
    }
}