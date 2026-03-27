package com.example.anchor.core.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes

// ── Toast helpers ─────────────────────────────────────────────

/** Shows a short [Toast] with [message]. */
fun Context.showToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Shows a short [Toast] using a string resource. */
fun Context.showToast(@StringRes resId: Int) =
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

/** Shows a long [Toast] with [message]. */
fun Context.showLongToast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

// ── Intent helpers ────────────────────────────────────────────

/**
 * Opens [url] in the device's default browser.
 * Does nothing if no browser app is installed.
 */
fun Context.openUrl(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

/**
 * Shares [text] using the system share sheet.
 * [title] appears as the share-sheet chooser title.
 */
fun Context.shareText(text: String, title: String = "") {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, title.ifBlank { null }).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}

// ── SharedPreferences helpers ─────────────────────────────────

/**
 * Convenience to read a [String] from the [name] SharedPreferences file.
 */
fun Context.readPref(name: String, key: String, default: String? = null): String? =
    getSharedPreferences(name, Context.MODE_PRIVATE).getString(key, default)

/**
 * Convenience to write a [String] to the [name] SharedPreferences file.
 */
fun Context.writePref(name: String, key: String, value: String) {
    getSharedPreferences(name, Context.MODE_PRIVATE)
        .edit()
        .putString(key, value)
        .apply()
}