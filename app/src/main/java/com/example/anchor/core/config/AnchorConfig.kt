package com.example.anchor.core.config

/**
 * Centralized configuration constants for the Anchor Media Server.
 */
object AnchorConfig {
    object Discovery {
        const val SEARCH_INTERVAL_MS = 30_000L
        const val STALE_CHECK_MS = 60_000L
        const val STALE_THRESHOLD_MS = 5 * 60_000L
        const val SOCKET_TIMEOUT_MS = 3_000
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
    }
    
    object Thumbnails {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val JPEG_QUALITY = 90
        const val CACHE_SIZE_MB = 50
        const val CACHE_SIZE_BYTES = CACHE_SIZE_MB * 1024L * 1024L
    }
    
    object Server {
        const val DEFAULT_PORT = 8080
        const val TIMEOUT_MS = 5_000L
        /** 1MB buffer for high-quality, smooth streaming. */
        const val STREAM_BUFFER_SIZE = 1024 * 1024
    }
}
