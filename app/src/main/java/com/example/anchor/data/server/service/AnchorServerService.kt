package com.example.anchor.data.server.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.anchor.presentation.AnchorApplication
import com.example.anchor.presentation.MainActivity
import com.example.anchor.R
import com.example.anchor.core.util.MulticastLockManager
import com.example.anchor.core.util.NetworkUtils
import com.example.anchor.domain.model.ServerStatus
import com.example.anchor.data.server.AnchorHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Foreground service that owns the [AnchorHttpServer] lifecycle.
 *
 * Changes from Phase 2:
 *  - [AnchorHttpServer] and [MulticastLockManager] are now injected via Koin
 *    (`by inject()`) instead of being constructed manually.
 *  - State is written to [AnchorServiceState] using domain [ServerStatus]
 *    mutators (setStarting / setRunning / setStopped / setError).
 *  - [AnchorServiceState] is imported from the new `server.service` package.
 */
class AnchorServerService : Service() {

    companion object {
        private const val TAG = "AnchorServerService"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "Anchor::ServerWakeLock"

        const val ACTION_START_SERVER = "com.example.anchor.START_SERVER"
        const val ACTION_STOP_SERVER = "com.example.anchor.STOP_SERVER"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_DIRECTORIES = "extra_directories"

        fun startServer(
            context: Context,
            port: Int = 8080,
            directories: ArrayList<String> = arrayListOf()
        ) {
            val intent = Intent(context, AnchorServerService::class.java).apply {
                action = ACTION_START_SERVER
                putExtra(EXTRA_PORT, port)
                putStringArrayListExtra(EXTRA_DIRECTORIES, directories)
            }
            context.startForegroundService(intent)
        }

        fun stopServer(context: Context) {
            context.startService(
                Intent(context, AnchorServerService::class.java).apply {
                    action = ACTION_STOP_SERVER
                }
            )
        }
    }

    // ── Koin injection ────────────────────────────────────────

    private val httpServer: AnchorHttpServer by inject()
    private val multicastLockManager: MulticastLockManager by inject()

    // ── Service state ─────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var serverPort = 8080
    private var sharedDirs: List<String> = emptyList()

    inner class LocalBinder : Binder() {
        fun getService(): AnchorServerService = this@AnchorServerService
    }

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        AnchorServiceState.addLog("Service initialised")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVER -> {
                serverPort = intent.getIntExtra(EXTRA_PORT, 8080)
                sharedDirs = intent.getStringArrayListExtra(EXTRA_DIRECTORIES) ?: emptyList()
                startForegroundWithNotification()
                startServer()
            }

            ACTION_STOP_SERVER -> {
                stopServer()
                stopSelf()
            }

            else -> startForegroundWithNotification()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopServer()
        serviceScope.cancel()
        AnchorServiceState.reset()
    }

    // ── Server start / stop ───────────────────────────────────

    private fun startServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                AnchorServiceState.setStarting()
                AnchorServiceState.addLog("Acquiring multicast lock…")
                multicastLockManager.acquire()
                acquireWakeLock()

                val localIp = NetworkUtils.getLocalIpAddress(this@AnchorServerService)
                if (localIp == null) {
                    AnchorServiceState.setError(
                        "Could not get local IP — check Wi-Fi connection"
                    )
                    return@launch
                }

                AnchorServiceState.addLog("Local IP: $localIp")

                // Wire shared directories into the HTTP server
                httpServer.clearSharedDirectories()

                var dirCount = 0
                if (sharedDirs.isNotEmpty()) {
                    sharedDirs.forEach { path ->
                        val dir = File(path)
                        when {
                            !dir.exists() -> AnchorServiceState.addLog("Skip (missing): $path")
                            !dir.isDirectory -> AnchorServiceState.addLog("Skip (not dir): $path")
                            else -> {
                                val alias = dir.name.lowercase().replace(" ", "_")
                                httpServer.addSharedDirectory(alias, dir)
                                AnchorServiceState.addLog("Sharing: ${dir.name}")
                                dirCount++
                            }
                        }
                    }
                } else {
                    dirCount = addDefaultDirectories()
                }

                AnchorServiceState.addLog("Starting HTTP server on port $serverPort ($dirCount dirs)…")
                httpServer.start()

                AnchorServiceState.setRunning(localIp, serverPort)
                AnchorServiceState.setSharedDirectories(sharedDirs)

                launch(Dispatchers.Main) { updateNotification() }

            } catch (e: Exception) {
                Log.e(TAG, "Server start failed", e)
                AnchorServiceState.setError("Server start failed: ${e.message}")
            }
        }
    }

    private fun stopServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                AnchorServiceState.addLog("Stopping server…")
                httpServer.stop()
                releaseWakeLock()
                multicastLockManager.forceRelease()
                AnchorServiceState.setStopped()
                AnchorServiceState.addLog("Server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server", e)
                AnchorServiceState.addLog("Stop error: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun addDefaultDirectories(): Int {
        var count = 0
        listOf(
            "DCIM" to "dcim",
            "Download" to "downloads",
            "Movies" to "movies",
            "Music" to "music",
            "Pictures" to "pictures"
        ).forEach { (folder, alias) ->
            val dir = File("/storage/emulated/0/$folder")
            if (dir.exists() && dir.isDirectory) {
                httpServer.addSharedDirectory(alias, dir)
                AnchorServiceState.addLog("Sharing default: $folder")
                count++
            }
        }
        if (count == 0) {
            // Start anyway — the server can run with zero directories
            AnchorServiceState.addLog("No default directories found — server will start empty")
        }
        return count
    }

    // ── Wake lock ─────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(10 * 60 * 1_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── Notification ──────────────────────────────────────────

    private fun startForegroundWithNotification() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else 0
        )
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AnchorServerService::class.java).apply {
                action = ACTION_STOP_SERVER
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (val s = AnchorServiceState.status.value) {
            is ServerStatus.Running -> "Serving at ${s.url}"
            is ServerStatus.Starting -> "Starting…"
            else -> "Server stopped"
        }

        return NotificationCompat.Builder(this, AnchorApplication.SERVER_CHANNEL_ID)
            .setContentTitle("Anchor Media Server")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification())
    }

    // ── Public API (via LocalBinder) ──────────────────────────

    fun addDirectory(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            val alias = dir.name.lowercase().replace(" ", "_")
            httpServer.addSharedDirectory(alias, dir)
            AnchorServiceState.addLog("Added: ${dir.name}")
        }
    }

    fun removeDirectory(alias: String) {
        httpServer.removeSharedDirectory(alias)
        AnchorServiceState.addLog("Removed: $alias")
    }

    fun isRunning(): Boolean =
        AnchorServiceState.status.value is ServerStatus.Running
}