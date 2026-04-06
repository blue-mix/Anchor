package com.example.anchor.data.source.local

import android.content.Context
import android.content.SharedPreferences
import com.example.anchor.core.config.AnchorConstants.SharedPreferences as Prefs
import com.example.anchor.core.config.AnchorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single access point for all [SharedPreferences] reads and writes in Anchor.
 */
class PreferencesDataSource(context: Context) {

    private val devicePrefs: SharedPreferences =
        context.getSharedPreferences(Prefs.DEVICE_PREFS, Context.MODE_PRIVATE)

    private val serverPrefs: SharedPreferences =
        context.getSharedPreferences(Prefs.SERVER_PREFS, Context.MODE_PRIVATE)

    private val userPrefs: SharedPreferences =
        context.getSharedPreferences(Prefs.USER_PREFS, Context.MODE_PRIVATE)

    // ── Device ────────────────────────────────────────────────

    /**
     * Returns the persisted device UUID, generating and saving a new one
     * when none exists.
     */
    suspend fun getOrCreateDeviceUuid(): String = withContext(Dispatchers.IO) {
        devicePrefs.getString(Prefs.Keys.DEVICE_UUID, null)
            ?: java.util.UUID.randomUUID().toString().also { uuid ->
                devicePrefs.edit().putString(Prefs.Keys.DEVICE_UUID, uuid).apply()
            }
    }

    // ── Server ────────────────────────────────────────────────

    fun getServerPort(): Int =
        serverPrefs.getInt(Prefs.Keys.SERVER_PORT, AnchorConfig.Server.DEFAULT_PORT)

    fun setServerPort(port: Int) {
        serverPrefs.edit().putInt(Prefs.Keys.SERVER_PORT, port).apply()
    }

    /**
     * Returns the set of persisted content-URI strings for SAF-picked directories.
     */
    fun getSharedDirectoryUris(): Set<String> =
        serverPrefs.getStringSet(Prefs.Keys.SHARED_DIR_URIS, emptySet()) ?: emptySet()

    fun setSharedDirectoryUris(uris: Set<String>) {
        serverPrefs.edit().putStringSet(Prefs.Keys.SHARED_DIR_URIS, uris).apply()
    }

    fun addSharedDirectoryUri(uri: String) {
        val current = getSharedDirectoryUris().toMutableSet()
        current.add(uri)
        setSharedDirectoryUris(current)
    }

    fun removeSharedDirectoryUri(uri: String) {
        val current = getSharedDirectoryUris().toMutableSet()
        current.remove(uri)
        setSharedDirectoryUris(current)
    }

    // ── User ──────────────────────────────────────────────────

    fun isOnboardingDone(): Boolean =
        userPrefs.getBoolean(Prefs.Keys.ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        userPrefs.edit().putBoolean(Prefs.Keys.ONBOARDING_DONE, done).apply()
    }

    fun getLastViewMode(): String =
        userPrefs.getString(Prefs.Keys.LAST_VIEW_MODE, "LIST") ?: "LIST"

    fun setLastViewMode(mode: String) {
        userPrefs.edit().putString(Prefs.Keys.LAST_VIEW_MODE, mode).apply()
    }
}
