package com.example.anchor.server

import android.content.Context
import com.example.anchor.core.config.AnchorConstants.SharedPreferences as Prefs
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.server.dlna.ContentDirectoryService
import com.example.anchor.server.dlna.DlnaDescriptionGenerator
import com.example.anchor.server.dlna.SsdpAnnouncer
import java.util.UUID

class DlnaManager(
    private val context: Context,
    private val directoryManager: SharedDirectoryManager
) {
    private var generator: DlnaDescriptionGenerator? = null
    private var contentDirectory: ContentDirectoryService? = null
    private var announcer: SsdpAnnouncer? = null

    fun start(port: Int) {
        val deviceUuid = getOrCreateDeviceUuid(context)
        val baseUrl = NetworkUtils.getLocalIpAddress(context)
            ?.let { "http://$it:$port" } ?: ""

        generator = DlnaDescriptionGenerator(context, deviceUuid, port)
        contentDirectory = ContentDirectoryService(baseUrl)
            .also { it.updateSharedDirectories(directoryManager.getAll()) }
        announcer = SsdpAnnouncer(context, port).apply { start() }
    }

    fun stop() {
        announcer?.stop()
        announcer = null
        generator = null
        contentDirectory = null
    }

    fun getGenerator(): DlnaDescriptionGenerator? = generator
    fun getContentDirectory(): ContentDirectoryService? = contentDirectory

    fun syncDirectories() {
        contentDirectory?.updateSharedDirectories(directoryManager.getAll())
    }

    private fun getOrCreateDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(Prefs.DEVICE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(Prefs.Keys.DEVICE_UUID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(Prefs.Keys.DEVICE_UUID, it).apply()
        }
    }
}
