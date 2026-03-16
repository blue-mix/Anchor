// app/src/main/java/com/example/anchor/media/ThumbnailGenerator.kt

package com.example.anchor.data.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generates and caches thumbnails for media files.
 */
class ThumbnailGenerator(private val context: Context) {

    companion object {
        private const val TAG = "ThumbnailGenerator"
        private const val THUMBNAIL_WIDTH = 320
        private const val THUMBNAIL_HEIGHT = 180
        private const val CACHE_SIZE_MB = 20
        private const val JPEG_QUALITY = 80
    }

    // In-memory LRU cache for thumbnails
    private val memoryCache: LruCache<String, ByteArray> = LruCache(CACHE_SIZE_MB * 1024 * 1024)

    // Disk cache directory
    private val cacheDir: File by lazy {
        File(context.cacheDir, "thumbnails").apply { mkdirs() }
    }

    /**
     * Gets a thumbnail for a video file.
     * Returns cached version if available, otherwise generates a new one.
     */
    suspend fun getVideoThumbnail(filePath: String): ByteArray? {
        val cacheKey = "video_${filePath.hashCode()}"

        // Check memory cache
        memoryCache.get(cacheKey)?.let { return it }

        // Check disk cache
        getDiskCached(cacheKey)?.let { cached ->
            memoryCache.put(cacheKey, cached)
            return cached
        }

        // Generate new thumbnail
        return withContext(Dispatchers.IO) {
            generateVideoThumbnail(filePath)?.also { thumbnail ->
                memoryCache.put(cacheKey, thumbnail)
                saveToDiskCache(cacheKey, thumbnail)
            }
        }
    }

    /**
     * Gets a thumbnail for an image file (resized version).
     */
    suspend fun getImageThumbnail(filePath: String): ByteArray? {
        val cacheKey = "image_${filePath.hashCode()}"

        // Check memory cache
        memoryCache.get(cacheKey)?.let { return it }

        // Check disk cache
        getDiskCached(cacheKey)?.let { cached ->
            memoryCache.put(cacheKey, cached)
            return cached
        }

        // Generate new thumbnail
        return withContext(Dispatchers.IO) {
            generateImageThumbnail(filePath)?.also { thumbnail ->
                memoryCache.put(cacheKey, thumbnail)
                saveToDiskCache(cacheKey, thumbnail)
            }
        }
    }

    /**
     * Gets album art embedded in an audio file.
     */
    suspend fun getAudioAlbumArt(filePath: String): ByteArray? {
        val cacheKey = "audio_${filePath.hashCode()}"

        // Check memory cache
        memoryCache.get(cacheKey)?.let { return it }

        // Check disk cache
        getDiskCached(cacheKey)?.let { cached ->
            memoryCache.put(cacheKey, cached)
            return cached
        }

        // Extract album art
        return withContext(Dispatchers.IO) {
            extractAlbumArt(filePath)?.also { albumArt ->
                memoryCache.put(cacheKey, albumArt)
                saveToDiskCache(cacheKey, albumArt)
            }
        }
    }

    /**
     * Generates a video thumbnail at approximately 10% into the video.
     */
    private fun generateVideoThumbnail(filePath: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)

            // Get duration and seek to 10% position
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val seekPosition = (duration * 0.1).toLong() * 1000 // Convert to microseconds

            // Get frame at position
            val bitmap = if (seekPosition > 0) {
                retriever.getFrameAtTime(seekPosition, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            bitmap?.let { scaledAndCompressed(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate video thumbnail: $filePath", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Generates a thumbnail for an image file.
     */
    private fun generateImageThumbnail(filePath: String): ByteArray? {
        return try {
            // First, decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT
            )
            options.inJustDecodeBounds = false

            // Decode with sample size
            val bitmap = BitmapFactory.decodeFile(filePath, options)
            bitmap?.let { scaledAndCompressed(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate image thumbnail: $filePath", e)
            null
        }
    }

    /**
     * Extracts embedded album art from an audio file.
     */
    private fun extractAlbumArt(filePath: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val albumArt = retriever.embeddedPicture

            if (albumArt != null) {
                // Resize if necessary
                val bitmap = BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                bitmap?.let { scaledAndCompressed(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract album art: $filePath", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Scales a bitmap and compresses it to JPEG bytes.
     */
    private fun scaledAndCompressed(bitmap: Bitmap): ByteArray {
        val scaled = if (bitmap.width > THUMBNAIL_WIDTH || bitmap.height > THUMBNAIL_HEIGHT) {
            val ratio = minOf(
                THUMBNAIL_WIDTH.toFloat() / bitmap.width,
                THUMBNAIL_HEIGHT.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        scaled.recycle()

        return stream.toByteArray()
    }

    /**
     * Calculates the optimal sample size for decoding.
     */
    private fun calculateInSampleSize(
        actualWidth: Int,
        actualHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (actualHeight > reqHeight || actualWidth > reqWidth) {
            val halfHeight = actualHeight / 2
            val halfWidth = actualWidth / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Gets cached thumbnail from disk.
     */
    private fun getDiskCached(key: String): ByteArray? {
        val file = File(cacheDir, key)
        return if (file.exists() && file.length() > 0) {
            try {
                file.readBytes()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Saves thumbnail to disk cache.
     */
    private fun saveToDiskCache(key: String, data: ByteArray) {
        try {
            File(cacheDir, key).writeBytes(data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache thumbnail to disk", e)
        }
    }

    /**
     * Clears all cached thumbnails.
     */
    fun clearCache() {
        memoryCache.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Gets the current cache size in bytes.
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}