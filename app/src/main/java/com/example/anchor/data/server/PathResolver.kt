package com.example.anchor.data.server

import com.example.anchor.core.result.Result
import com.example.anchor.domain.model.Alias
import com.example.anchor.domain.model.MediaPath
import io.ktor.server.application.ApplicationCall
import java.io.File
import java.net.URLDecoder

class PathResolver(private val directoryManager: SharedDirectoryManager) {

    data class ResolvedPath(
        val alias: Alias,
        val relativePath: String,
        val baseDir: File,
        val mediaPath: MediaPath
    )

    fun resolve(call: ApplicationCall): Result<ResolvedPath> {
        val aliasValue = call.parameters["alias"]
            ?: return Result.Error("Missing alias parameter")

        val alias = Alias(aliasValue)

        val relativePath = call.parameters.getAll("path")
            ?.joinToString("/")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: ""

        val baseDir = directoryManager.getAll()[alias.value]
            ?: return Result.Error("Unknown alias: ${alias.value}")

        return Result.Success(
            ResolvedPath(
                alias = alias,
                relativePath = relativePath,
                baseDir = baseDir,
                mediaPath = MediaPath.from(alias, relativePath)
            )
        )
    }
}
