package com.example.anchor.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * Returns the list of media permissions required based on Android version.
     * Android 13+ (API 33): Granular media permissions
     * Android 12 and below: READ_EXTERNAL_STORAGE
     */
    fun getRequiredMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Returns notification permission for Android 13+.
     */
    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }

    /**
     * All permissions needed for the app to function.
     */
    fun getAllRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        permissions.addAll(getRequiredMediaPermissions())
        getNotificationPermission()?.let { permissions.add(it) }
        return permissions
    }

    /**
     * Checks if all required permissions are granted.
     */
    fun areAllPermissionsGranted(context: Context): Boolean {
        return getAllRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if media permissions are granted.
     */
    fun areMediaPermissionsGranted(context: Context): Boolean {
        return getRequiredMediaPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if notification permission is granted.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        val permission = getNotificationPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns list of permissions that are not yet granted.
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getAllRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}