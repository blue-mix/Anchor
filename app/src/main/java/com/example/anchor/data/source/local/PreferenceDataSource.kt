package com.example.anchor.data.source.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single access point for all [SharedPreferences] reads and writes in Anchor.
 *
 * Centralising prefs here means:
 *  - Key names are defined as constants in one file.
 *  - ViewModels never touch SharedPreferences directly.
 *  - The whole thing can be faked in tests with an in-memory map.
 */
class PreferencesDataSource(context: Context) {

    companion object {
        // File names
        private const val PREFS_DEVICE = "anchor_device"
        private const val PREFS_SERVER = "anchor_server"
        private const val PREFS_USER = "anchor_user"

        // Keys — device
        const val KEY_DEVICE_UUID = "uuid"

        // Keys — server
        const val KEY_SERVER_PORT = "port"
        const val KEY_SHARED_DIR_URIS = "shared_dir_uris"

        // Keys — user
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_LAST_VIEW_MODE = "last_view_mode"

        // Defaults
        const val DEFAULT_PORT = 8080
    }

    private val devicePrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)

    private val serverPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_SERVER, Context.MODE_PRIVATE)

    private val userPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_USER, Context.MODE_PRIVATE)

    // ── Device ────────────────────────────────────────────────

    /**
     * Returns the persisted device UUID, generating and saving a new one
     * when none exists.
     */
    suspend fun getOrCreateDeviceUuid(): String = withContext(Dispatchers.IO) {
        devicePrefs.getString(KEY_DEVICE_UUID, null)
            ?: java.util.UUID.randomUUID().toString().also { uuid ->
                devicePrefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
            }
    }

    // ── Server ────────────────────────────────────────────────

    fun getServerPort(): Int =
        serverPrefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)

    fun setServerPort(port: Int) {
        serverPrefs.edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    /**
     * Returns the set of persisted content-URI strings for SAF-picked directories.
     */
    fun getSharedDirectoryUris(): Set<String> =
        serverPrefs.getStringSet(KEY_SHARED_DIR_URIS, emptySet()) ?: emptySet()

    fun setSharedDirectoryUris(uris: Set<String>) {
        serverPrefs.edit().putStringSet(KEY_SHARED_DIR_URIS, uris).apply()
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
        userPrefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        userPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun getLastViewMode(): String =
        userPrefs.getString(KEY_LAST_VIEW_MODE, "LIST") ?: "LIST"

    fun setLastViewMode(mode: String) {
        userPrefs.edit().putString(KEY_LAST_VIEW_MODE, mode).apply()
    }
}