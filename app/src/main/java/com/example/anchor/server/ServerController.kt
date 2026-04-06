package com.example.anchor.server

import android.content.Context
import com.example.anchor.domain.model.ServerConfig
import com.example.anchor.server.service.AnchorServerService

interface ServerController {
    fun start(config: ServerConfig)
    fun stop()
}

class AndroidServerController(private val context: Context) : ServerController {
    override fun start(config: ServerConfig) {
        AnchorServerService.startServer(
            context = context,
            port = config.port,
            directories = config.sharedDirectories.values
                .map { it.absolutePath }
                .toCollection(ArrayList())
        )
    }

    override fun stop() {
        AnchorServerService.stopServer(context)
    }
}
