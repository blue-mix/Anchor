package com.example.anchor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.anchor.core.util.MulticastLockManager

class AnchorApplication : Application() {

    companion object {
        const val SERVER_CHANNEL_ID = "anchor_server_channel"
        const val MEDIA_CHANNEL_ID = "anchor_media_channel"

        // Global instance for easy access
        lateinit var instance: AnchorApplication
            private set
    }

    // Lazy-initialized multicast lock manager
    val multicastLockManager: MulticastLockManager by lazy {
        MulticastLockManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val serverChannel = NotificationChannel(
            SERVER_CHANNEL_ID,
            "Media Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Anchor media server is running"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val mediaChannel = NotificationChannel(
            MEDIA_CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows media playback controls"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(listOf(serverChannel, mediaChannel))
    }
}