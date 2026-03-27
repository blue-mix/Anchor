package com.example.anchor.domain.model

/**
 * Immutable value object representing all the parameters needed to start
 * the Anchor HTTP server.
 *
 * Created by [DashboardViewModel] from user input and passed to
 * [StartServerUseCase].  The domain layer validates it; the server layer
 * consumes it.
 */
data class ServerConfig(
    /** TCP port the HTTP server listens on. Must be in [1024, 65535]. */
    val port: Int = DEFAULT_PORT,

    /**
     * Directories to share, keyed by their URL alias.
     * Example: "movies" → File("/storage/emulated/0/Movies")
     */
    val sharedDirectories: Map<String, SharedDirectory> = emptyMap(),

    /**
     * Human-readable name advertised via UPnP/SSDP.
     * Defaults to the device model name set at startup.
     */
    val serverName: String = "Anchor Media Server"
) {
    init {
        require(port in MIN_PORT..MAX_PORT) {
            "Port must be in [$MIN_PORT, $MAX_PORT], got $port"
        }
    }

    /** Returns a copy with [port] clamped to the valid range. */
    fun withSafePort(port: Int) = copy(port = port.coerceIn(MIN_PORT, MAX_PORT))

    /** Returns true when at least one directory is configured. */
    val hasDirectories: Boolean get() = sharedDirectories.isNotEmpty()

    companion object {
        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535
    }
}

/**
 * A single shared directory entry inside [ServerConfig].
 */
data class SharedDirectory(
    /** URL-safe alias used in API paths (e.g. "movies"). */
    val alias: String,

    /** Human-readable display name (e.g. the folder's actual name). */
    val displayName: String,

    /** Absolute filesystem path. */
    val absolutePath: String,

    /**
     * Content-URI string for SAF-based access (content://…).
     * Null when accessed via a direct file path.
     */
    val contentUri: String? = null,

    /** Count of direct (non-recursive) files in this directory. */
    val fileCount: Int = 0
) {
    /**
     * True when this directory was added via Android's Storage Access Framework
     * and must be accessed through a ContentResolver rather than [java.io.File].
     */
    val isSafUri: Boolean get() = contentUri != null
}

// ── ServerStatus ──────────────────────────────────────────────

/**
 * Observable state of the running (or stopped) server.
 * Used by [AnchorServiceState] as the single source of truth for the UI.
 */
sealed interface ServerStatus {

    /** Server is not running. */
    data object Stopped : ServerStatus

    /** Server startup is in progress. */
    data object Starting : ServerStatus

    /**
     * Server is running and accepting connections.
     * @param ipAddress  Local IPv4 address.
     * @param port       Listening port.
     */
    data class Running(
        val ipAddress: String,
        val port: Int
    ) : ServerStatus {
        val url: String get() = "http://$ipAddress:$port"
    }

    /** Server stopped due to an error. */
    data class Error(val message: String) : ServerStatus
}