package com.example.anchor.server

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
import com.example.anchor.AnchorApplication
import com.example.anchor.MainActivity
import com.example.anchor.R
import com.example.anchor.core.util.MulticastLockManager
import com.example.anchor.core.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

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
            val intent = Intent(context, AnchorServerService::class.java).apply {
                action = ACTION_STOP_SERVER
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private lateinit var multicastLockManager: MulticastLockManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var serverPort: Int = 8080
    private var sharedDirectories: List<String> = emptyList()
    private var isServerRunning = false

    // HTTP Server instance
    private var httpServer: AnchorHttpServer? = null

    inner class LocalBinder : Binder() {
        fun getService(): AnchorServerService = this@AnchorServerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        multicastLockManager = MulticastLockManager(this)
        AnchorServiceState.addLog("Service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVER -> {
                serverPort = intent.getIntExtra(EXTRA_PORT, 8080)
                sharedDirectories = intent.getStringArrayListExtra(EXTRA_DIRECTORIES) ?: emptyList()

                startForegroundWithNotification()
                startServer()
            }

            ACTION_STOP_SERVER -> {
                stopServer()
                stopSelf()
            }

            else -> {
                startForegroundWithNotification()
            }
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

    private fun startForegroundWithNotification() {
        val notification = createNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
    }

    private fun createNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AnchorServerService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val serverUrl = AnchorServiceState.state.value.serverUrl ?: "Starting..."

        return NotificationCompat.Builder(this, AnchorApplication.SERVER_CHANNEL_ID)
            .setContentTitle("Anchor Media Server")
            .setContentText(if (isServerRunning) "Serving at $serverUrl" else "Starting server...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Server", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startServer() {
        if (isServerRunning) {
            Log.w(TAG, "Server already running")
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                multicastLockManager.acquire()
                AnchorServiceState.addLog("Multicast lock acquired")

                acquireWakeLock()

                val localIp = NetworkUtils.getLocalIpAddress(this@AnchorServerService)
                if (localIp == null) {
                    AnchorServiceState.setError("Failed to get local IP address. Check Wi-Fi connection.")
                    AnchorServiceState.addLog("Failed to get local IP", LogLevel.ERROR)
                    return@launch
                }

                // Initialize and configure HTTP server
                httpServer = AnchorHttpServer(this@AnchorServerService, serverPort).apply {
                    // Add shared directories
                    sharedDirectories.forEachIndexed { index, path ->
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory) {
                            val alias = dir.name.lowercase().replace(" ", "_")
                            addSharedDirectory(alias, dir)
                            AnchorServiceState.addLog("Sharing: ${dir.name}")
                        }
                    }

                    // Add default directories if none specified
                    if (sharedDirectories.isEmpty()) {
                        addDefaultDirectories(this)
                    }
                }

                // Start the HTTP server
                httpServer?.start()

                AnchorServiceState.setRunning(true, localIp, serverPort)
                AnchorServiceState.setSharedDirectories(sharedDirectories)

                isServerRunning = true
                AnchorServiceState.addLog("Server started at http://$localIp:$serverPort")

                launch(Dispatchers.Main) {
                    updateNotification()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                AnchorServiceState.setError("Failed to start server: ${e.message}")
                AnchorServiceState.addLog("Server start failed: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun addDefaultDirectories(server: AnchorHttpServer) {
        // Add common media directories
        val dcim = File("/storage/emulated/0/DCIM")
        val download = File("/storage/emulated/0/Download")
        val movies = File("/storage/emulated/0/Movies")
        val music = File("/storage/emulated/0/Music")
        val pictures = File("/storage/emulated/0/Pictures")

        if (dcim.exists()) server.addSharedDirectory("dcim", dcim)
        if (download.exists()) server.addSharedDirectory("downloads", download)
        if (movies.exists()) server.addSharedDirectory("movies", movies)
        if (music.exists()) server.addSharedDirectory("music", music)
        if (pictures.exists()) server.addSharedDirectory("pictures", pictures)
    }

    private fun stopServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                AnchorServiceState.addLog("Stopping server...")

                httpServer?.stop()
                httpServer = null

                isServerRunning = false

                releaseWakeLock()
                multicastLockManager.forceRelease()

                AnchorServiceState.setRunning(false)
                AnchorServiceState.addLog("Server stopped")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server", e)
                AnchorServiceState.addLog("Error stopping server: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L)
            }
            Log.d(TAG, "Wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    fun refreshWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.acquire(10 * 60 * 1000L)
            }
        }
    }

    fun isRunning(): Boolean = isServerRunning
    fun getPort(): Int = serverPort
    fun getSharedDirectories(): List<String> = sharedDirectories

    /**
     * Adds a directory to the running server.
     */
    fun addDirectory(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            val alias = dir.name.lowercase().replace(" ", "_")
            httpServer?.addSharedDirectory(alias, dir)
            AnchorServiceState.addLog("Added directory: ${dir.name}")
        }
    }

    /**
     * Removes a directory from the running server.
     */
    fun removeDirectory(alias: String) {
        httpServer?.removeSharedDirectory(alias)
        AnchorServiceState.addLog("Removed directory: $alias")
    }
}
