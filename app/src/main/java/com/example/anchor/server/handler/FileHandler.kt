package com.example.anchor.server.handler

import com.example.anchor.core.util.MimeTypeUtils
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
import java.net.URLDecoder

/**
 * Handles all files and stream route logic.
 */
class FileHandler {

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024 // 1MB buffer for smoother streaming
    }

    suspend fun handleServe(call: ApplicationCall, sharedDirectories: Map<String, File>) {
        val (file, _) = resolveFile(call, sharedDirectories) ?: return
        AnchorServiceState.addLog("Serving: ${file.name}")
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentType, MimeTypeUtils.getMimeType(file.name))
        call.respondFile(file)
    }

    suspend fun handleStream(call: ApplicationCall, sharedDirectories: Map<String, File>) {
        val (file, _) = resolveFile(call, sharedDirectories) ?: return
        val mimeType = MimeTypeUtils.getMimeType(file.name)
        val fileLength = file.length()
        val range = call.request.headers[HttpHeaders.Range]

        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").split("-")
            val start = spec[0].toLongOrNull() ?: 0L
            val end = spec.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
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

    internal suspend fun resolveFile(
        call: ApplicationCall,
        sharedDirectories: Map<String, File>
    ): Pair<File, File>? {
        val alias = call.parameters["alias"]
            ?: run { call.respond(HttpStatusCode.BadRequest, "Missing alias"); return null }

        val relativePath = call.parameters.getAll("path")
            ?.joinToString("/")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: ""

        val baseDir = sharedDirectories[alias]
            ?: run { call.respond(HttpStatusCode.NotFound, "Unknown alias: $alias"); return null }

        val file = File(baseDir, relativePath)

        if (!file.exists() || file.isDirectory) {
            call.respond(HttpStatusCode.NotFound, "File not found: $relativePath")
            return null
        }

        val canonical = file.canonicalPath
        val base = baseDir.canonicalPath
        if (canonical != base && !canonical.startsWith(base + File.separator)) {
            call.respond(HttpStatusCode.Forbidden, "Access denied")
            return null
        }

        return file to baseDir
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
                val buffer = ByteArray(64 * 1024) // 1MB chunk
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