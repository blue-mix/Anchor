package com.example.anchor.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the current state of the Anchor server service.
 */
data class ServerState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val localIpAddress: String? = null,
    val serverUrl: String? = null,
    val sharedDirectories: List<String> = emptyList(),
    val connectedClients: Int = 0,
    val errorMessage: String? = null
)

/**
 * Singleton object to share server state between the Service and UI.
 * Uses StateFlow for reactive updates in Compose.
 */
object AnchorServiceState {

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun updateState(update: (ServerState) -> ServerState) {
        _state.value = update(_state.value)
    }

    fun setRunning(running: Boolean, ip: String? = null, port: Int = 8080) {
        _state.value = _state.value.copy(
            isRunning = running,
            localIpAddress = ip,
            port = port,
            serverUrl = ip?.let { "http://$it:$port" }
        )
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    fun setSharedDirectories(directories: List<String>) {
        _state.value = _state.value.copy(sharedDirectories = directories)
    }

    fun incrementClients() {
        _state.value = _state.value.copy(
            connectedClients = _state.value.connectedClients + 1
        )
    }

    fun decrementClients() {
        _state.value = _state.value.copy(
            connectedClients = maxOf(0, _state.value.connectedClients - 1)
        )
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            message = message,
            level = level
        )
        _logs.value = (_logs.value + entry).takeLast(100) // Keep last 100 logs
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun reset() {
        _state.value = ServerState()
        _logs.value = emptyList()
    }
}

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val level: LogLevel
)

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}