package com.example.anchor.core.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Manages the Wi-Fi Multicast Lock required for UPnP/SSDP discovery.
 *
 * Android filters out multicast packets by default to conserve battery.
 * Acquiring a [WifiManager.MulticastLock] allows the device to receive
 * SSDP packets on 239.255.255.250:1900 used by UPnP device discovery.
 *
 * Usage:
 *   val manager = MulticastLockManager(context)  // via Koin: single { ... }
 *   manager.acquire()          // before starting discovery / server
 *   manager.release()          // when done
 *   manager.forceRelease()     // in Service.onDestroy()
 *
 * Thread-safety: all public methods are synchronized.
 */
class MulticastLockManager(context: Context) {

    companion object {
        private const val TAG = "MulticastLockManager"
        private const val LOCK_TAG = "AnchorMulticastLock"
        private const val MAX_ACQUIRE_MS = 10L * 60L * 1000L   // 10 minutes
    }

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var acquireCount = 0
    private val mutex = Any()

    // ── Public API ────────────────────────────────────────────

    /**
     * Acquires the multicast lock.  Reference-counted — must be balanced
     * with an equal number of [release] calls.
     *
     * @return true if the lock is held after this call, false on error.
     */
    fun acquire(): Boolean = synchronized(mutex) {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock(LOCK_TAG).apply {
                    setReferenceCounted(true)
                }
            }
            val lock = multicastLock ?: return false
            if (!lock.isHeld) {
                lock.acquire()
                Log.d(TAG, "Multicast lock acquired")
            }
            acquireCount++
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
            false
        }
    }

    /**
     * Decrements the reference count and releases the lock when it reaches 0.
     * Safe to call more times than [acquire].
     */
    fun release() = synchronized(mutex) {
        try {
            if (acquireCount > 0) acquireCount--
            if (acquireCount == 0) {
                multicastLock?.let { lock ->
                    if (lock.isHeld) {
                        lock.release()
                        Log.d(TAG, "Multicast lock released")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock", e)
        }
    }

    /**
     * Forces the lock to be released regardless of the reference count.
     * Call this from Service.onDestroy() to guarantee cleanup.
     */
    fun forceRelease() = synchronized(mutex) {
        try {
            multicastLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d(TAG, "Multicast lock force-released")
                }
            }
            acquireCount = 0
            multicastLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force-release multicast lock", e)
        }
    }

    /**
     * Returns true if the lock is currently held.
     */
    val isHeld: Boolean
        get() = synchronized(mutex) { multicastLock?.isHeld == true }

    /**
     * Current reference count (for diagnostics / testing).
     */
    val referenceCount: Int
        get() = synchronized(mutex) { acquireCount }
}