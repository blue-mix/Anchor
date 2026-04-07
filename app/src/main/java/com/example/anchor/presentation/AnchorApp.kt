package com.example.anchor.presentation

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.anchor.presentation.AnchorApplication.Companion.instance
import com.example.anchor.di.appModule
import com.example.anchor.di.networkModule
import com.example.anchor.di.serverModule
import com.example.anchor.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application class for Anchor.
 *
 * Changes from original:
 *  - [startKoin] block added — registers all four Koin modules so the
 *    dependency graph is available before any Activity or Service starts.
 *  - The manual [multicastLockManager] lazy property is removed; it is
 *    now a Koin singleton registered in [appModule] and injected wherever
 *    needed (e.g. [AnchorServerService]).
 *  - [instance] companion property retained for the rare cases (notification
 *    channel IDs) where a static reference is still needed.
 */
class AnchorApplication : Application() {

    companion object {
        const val SERVER_CHANNEL_ID = "anchor_server_channel"
        const val MEDIA_CHANNEL_ID = "anchor_media_channel"

        lateinit var instance: AnchorApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        startKoin {
//            // Verbose in debug builds, silent in release
//            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@AnchorApplication)
            modules(
                appModule,
                networkModule,
                serverModule,
                viewModelModule
            )
        }

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

        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(serverChannel, mediaChannel))
    }
}