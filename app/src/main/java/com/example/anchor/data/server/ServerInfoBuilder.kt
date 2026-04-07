package com.example.anchor.data.server

import android.os.Build
import com.example.anchor.data.dto.ServerInfoDto

class ServerInfoBuilder(
    private val directoryManager: SharedDirectoryManager
) {
    fun build(): ServerInfoDto {
        val stats = directoryManager.getAll().values
            .flatMap { it.walkTopDown().filter { file -> file.isFile } }
            .fold(Pair(0, 0L)) { (count, size), file ->
                Pair(count + 1, size + file.length())
            }

        val (totalFiles, totalSize) = stats

        return ServerInfoDto(
            name = "Anchor Media Server",
            version = "1.0.0",
            deviceName = Build.MODEL,
            sharedDirectories = directoryManager.getAll().keys.toList(),
            totalFiles = totalFiles,
            totalSize = totalSize
        )
    }
}
