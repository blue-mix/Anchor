package com.example.anchor.core.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Manages the Wi-Fi Multicast Lock required for UPnP/SSDP discovery.
 *
 * By default, Android filters out multicast packets to save battery.
 * Acquiring a MulticastLock allows the device to receive SSDP multicast
 * messages (239.255.255.250:1900) used for UPnP device discovery.
 *
 * IMPORTANT: The lock must be released when not needed to preserve battery.
 */
class MulticastLockManager(context: Context) {

    companion object {
        private const val TAG = "MulticastLockManager"
        private const val LOCK_TAG = "AnchorMulticastLock"
    }

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var lockCount = 0
    private val lock = Any()

    /**
     * Acquires the multicast lock. Can be called multiple times;
     * uses reference counting to track acquisitions.
     *
     * @return true if the lock is held after this call
     */
    fun acquire(): Boolean {
        synchronized(lock) {
            return try {
                if (multicastLock == null) {
                    multicastLock = wifiManager.createMulticastLock(LOCK_TAG).apply {
                        setReferenceCounted(true)
                    }
                }

                multicastLock?.let {
                    if (!it.isHeld) {
                        it.acquire()
                        Log.d(TAG, "Multicast lock acquired")
                    }
                    lockCount++
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire multicast lock", e)
                false
            }
        }
    }

    /**
     * Releases the multicast lock. Uses reference counting,
     * so the lock is only fully released when all acquirers have released.
     */
    fun release() {
        synchronized(lock) {
            try {
                if (lockCount > 0) {
                    lockCount--
                }

                if (lockCount == 0) {
                    multicastLock?.let {
                        if (it.isHeld) {
                            it.release()
                            Log.d(TAG, "Multicast lock released")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release multicast lock", e)
            }
        }
    }

    /**
     * Forces release of the multicast lock regardless of reference count.
     * Use when shutting down the service.
     */
    fun forceRelease() {
        synchronized(lock) {
            try {
                multicastLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "Multicast lock force released")
                    }
                }
                lockCount = 0
                multicastLock = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force release multicast lock", e)
            }
        }
    }

    /**
     * Checks if the multicast lock is currently held.
     */
    fun isHeld(): Boolean {
        synchronized(lock) {
            return multicastLock?.isHeld == true
        }
    }
}