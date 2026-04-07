package com.example.anchor.data.server

import android.util.Log
import java.io.File

class SharedDirectoryManager {
    companion object {
        private const val TAG = "SharedDirectoryManager"
    }

    private val directories = mutableMapOf<String, File>()

    fun add(alias: String, directory: File): Boolean {
        return if (directory.exists() && directory.isDirectory) {
            directories[alias] = directory
            Log.d(TAG, "Shared: $alias → ${directory.absolutePath}")
            true
        } else {
            Log.w(TAG, "Invalid directory: ${directory.absolutePath}")
            false
        }
    }

    fun remove(alias: String) {
        directories.remove(alias)
    }

    fun getAll(): Map<String, File> = directories.toMap()

    fun clear() {
        directories.clear()
    }
}
