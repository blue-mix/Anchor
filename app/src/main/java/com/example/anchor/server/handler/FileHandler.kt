package com.example.anchor.server.handler

import com.example.anchor.core.config.AnchorConfig
import com.example.anchor.core.extension.isDescendantOf
import com.example.anchor.core.util.MimeTypeUtils
import com.example.anchor.server.PathResolver
import com.example.anchor.server.service.AnchorServiceState
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Handles all file serving and range-aware streaming logic.
 */
class FileHandler(private val pathResolver: PathResolver) {

    suspend fun handleServe(call: ApplicationCall) {
        pathResolver.resolve(call)
            .onSuccess { (_, relativePath, baseDir) ->
                val file = File(baseDir, relativePath)
                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound, "File not found")
                    return@onSuccess
                }

                if (!file.isDescendantOf(baseDir)) {
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@onSuccess
                }

                AnchorServiceState.addLog("Serving: ${file.name}")
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentType, MimeTypeUtils.getMimeType(file.name))
                call.respondFile(file)
            }
            .onError { message, _ ->
                call.respond(HttpStatusCode.BadRequest, message)
            }
    }

    suspend fun handleStream(call: ApplicationCall) {
        pathResolver.resolve(call)
            .onSuccess { (_, relativePath, baseDir) ->
                val file = File(baseDir, relativePath)
                if (!file.exists() || file.isDirectory) {
                    call.respond(HttpStatusCode.NotFound, "File not found")
                    return@onSuccess
                }

                if (!file.isDescendantOf(baseDir)) {
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
                    return@onSuccess
                }

                val mimeType = MimeTypeUtils.getMimeType(file.name)
                val fileLength = file.length()
                val range = call.request.headers[HttpHeaders.Range]

                if (range != null && range.startsWith("bytes=")) {
                    val rangeParts = range.removePrefix("bytes=").split("-")
                    val start = rangeParts[0].toLongOrNull() ?: 0L
                    val end = rangeParts.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
                    val length = end - start + 1

                    call.response.status(HttpStatusCode.PartialContent)
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileLength")
                    call.response.header(HttpHeaders.ContentLength, length.toString())
                    call.response.header(HttpHeaders.ContentType, mimeType)
                    call.respond(RangeFileContent(file, start, length))
                } else {
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentLength, fileLength.toString())
                    call.response.header(HttpHeaders.ContentType, mimeType)
                    call.respondFile(file)
                }
            }
            .onError { message, _ ->
                call.respond(HttpStatusCode.BadRequest, message)
            }
    }
}

private class RangeFileContent(
    private val file: File,
    private val start: Long,
    private val length: Long
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = length

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(AnchorConfig.Server.STREAM_BUFFER_SIZE)
                var remaining = length
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    channel.writeFully(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }
}
