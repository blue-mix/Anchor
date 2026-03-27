package com.example.anchor.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.anchor.core.util.PermissionUtils.getAllRequiredPermissions

/**
 * Centralised permission helpers for Anchor.
 *
 * Keeps API-version branching in one place so ViewModels and Activities
 * never have to repeat the same Build.VERSION_SDK_INT checks.
 */
object PermissionUtils {

    // ── Permission lists ──────────────────────────────────────

    /**
     * Media permissions, adjusted for the running Android version.
     *
     * - API 33+ (Android 13): granular per-media-type permissions
     * - API 29–32: READ_EXTERNAL_STORAGE
     * - API < 29:  READ_EXTERNAL_STORAGE (same, broader scope)
     */
    fun getRequiredMediaPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * POST_NOTIFICATIONS is required from API 33+.
     * Returns null on older versions — callers should skip requesting it.
     */
    fun getNotificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS
        else
            null

    /**
     * All permissions needed for full app functionality.
     * Notification permission is optional; the server still works without it.
     */
    fun getAllRequiredPermissions(): List<String> = buildList {
        addAll(getRequiredMediaPermissions())
        getNotificationPermission()?.let { add(it) }
    }

    // ── Grant checks ──────────────────────────────────────────

    /**
     * Returns true if every permission in [getAllRequiredPermissions] is granted.
     * Use this to decide whether to show onboarding.
     */
    fun areAllPermissionsGranted(context: Context): Boolean =
        getAllRequiredPermissions().all { isGranted(context, it) }

    /**
     * Returns true if all media permissions are granted.
     * The server can start sharing without notification permission.
     */
    fun areMediaPermissionsGranted(context: Context): Boolean =
        getRequiredMediaPermissions().all { isGranted(context, it) }

    /**
     * Returns true if the notification permission is granted (or not required).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        val permission = getNotificationPermission() ?: return true
        return isGranted(context, permission)
    }

    /**
     * Returns only the permissions from [getAllRequiredPermissions] that are
     * not yet granted — ready to pass to [ActivityResultContracts.RequestMultiplePermissions].
     */
    fun getMissingPermissions(context: Context): List<String> =
        getAllRequiredPermissions().filter { !isGranted(context, it) }

    /**
     * Returns only the missing media permissions.
     */
    fun getMissingMediaPermissions(context: Context): List<String> =
        getRequiredMediaPermissions().filter { !isGranted(context, it) }

    // ── Internal helper ───────────────────────────────────────

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
}