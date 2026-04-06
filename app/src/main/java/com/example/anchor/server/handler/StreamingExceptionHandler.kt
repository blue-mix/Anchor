// server/handler/StreamingExceptionHandler.kt
package com.example.anchor.server.handler

import io.ktor.utils.io.ClosedByteChannelException
import java.io.IOException

object StreamingExceptionHandler {
    /**
     * Returns true if this is an expected client disconnection during streaming.
     * These exceptions should be logged at DEBUG level, not ERROR.
     */
    fun isExpectedDisconnection(cause: Throwable): Boolean {
        return when {
            // Client closed connection while streaming
            cause is ClosedByteChannelException -> true

            // Network errors during streaming
            cause is IOException && (
                    cause.message?.contains("Connection reset by peer", ignoreCase = true) == true ||
                            cause.message?.contains("Broken pipe", ignoreCase = true) == true ||
                            cause.message?.contains("Connection reset", ignoreCase = true) == true
                    ) -> true

            // Wrapped exceptions
            cause.cause != null && isExpectedDisconnection(cause.cause!!) -> true

            else -> false
        }
    }

    fun getLogMessage(cause: Throwable): String {
        return when {
            cause.message?.contains("reset by peer") == true ->
                "Client disconnected during streaming (normal behavior - user likely seeked/stopped)"
            cause.message?.contains("Broken pipe") == true ->
                "Client closed connection during transfer"
            else ->
                "Streaming connection closed: ${cause.message}"
        }
    }
}