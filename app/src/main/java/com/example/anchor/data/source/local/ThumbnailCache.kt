package com.example.anchor.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import com.example.anchor.core.config.AnchorConfig
import com.example.anchor.core.result.Result
import com.example.anchor.core.result.resultOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Generates and caches JPEG thumbnails for video, image, and audio files.
 */
class ThumbnailCache(context: Context) {

    companion object {
        private const val TAG = "ThumbnailCache"
    }

    private val memoryCache = object : LruCache<String, ByteArray>(AnchorConfig.Thumbnails.CACHE_SIZE_BYTES.toInt()) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return value.size
        }
    }

    private val diskCacheDir: File =
        File(context.cacheDir, "thumbnails").also { it.mkdirs() }

    suspend fun getVideoThumbnail(filePath: String): Result<ByteArray> =
        getCached("video_${filePath.hashCode()}") {
            generateVideoThumbnail(filePath)
        }

    suspend fun getImageThumbnail(filePath: String): Result<ByteArray> =
        getCached("image_${filePath.hashCode()}") {
            generateImageThumbnail(filePath)
        }

    suspend fun getAudioAlbumArt(filePath: String): Result<ByteArray> =
        getCached("audio_${filePath.hashCode()}") {
            extractAlbumArt(filePath)
        }

    fun clearAll() {
        memoryCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Thumbnail cache cleared")
    }

    fun diskCacheSizeBytes(): Long =
        diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    private suspend fun getCached(
        key: String,
        generate: suspend () -> ByteArray?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        memoryCache.get(key)?.let { return@withContext Result.Success(it) }

        readDiskCache(key)?.let { cached ->
            memoryCache.put(key, cached)
            return@withContext Result.Success(cached)
        }

        resultOf {
            val bytes = generate()
                ?: throw IllegalStateException("Thumbnail generation returned null")
            memoryCache.put(key, bytes)
            writeDiskCache(key, bytes)
            bytes
        }
    }

    private fun generateVideoThumbnail(filePath: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val seekUs = (durationMs * 0.1).toLong() * 1_000
            val bitmap = retriever.getFrameAtTime(
                seekUs.coerceAtLeast(0),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            bitmap?.scaleAndCompress()
        } catch (e: Exception) {
            Log.w(TAG, "Video thumbnail failed: $filePath", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun generateImageThumbnail(filePath: String): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(
                    bounds.outWidth, bounds.outHeight,
                    AnchorConfig.Thumbnails.WIDTH, AnchorConfig.Thumbnails.HEIGHT
                )
            }
            BitmapFactory.decodeFile(filePath, options)?.scaleAndCompress()
        } catch (e: Exception) {
            Log.w(TAG, "Image thumbnail failed: $filePath", e)
            null
        }
    }

    private fun extractAlbumArt(filePath: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.embeddedPicture?.let { raw ->
                BitmapFactory.decodeByteArray(raw, 0, raw.size)?.scaleAndCompress()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Album art extraction failed: $filePath", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun Bitmap.scaleAndCompress(): ByteArray {
        val scaled = if (width > AnchorConfig.Thumbnails.WIDTH || height > AnchorConfig.Thumbnails.HEIGHT) {
            val ratio = minOf(
                AnchorConfig.Thumbnails.WIDTH.toFloat() / width,
                AnchorConfig.Thumbnails.HEIGHT.toFloat() / height
            )
            val newW = (width * ratio).toInt().coerceAtLeast(1)
            val newH = (height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(this, newW, newH, true)
                .also { if (it !== this) recycle() }
        } else {
            this
        }

        return ByteArrayOutputStream()
            .also { scaled.compress(Bitmap.CompressFormat.JPEG, AnchorConfig.Thumbnails.JPEG_QUALITY, it) }
            .toByteArray()
            .also { scaled.recycle() }
    }

    private fun calculateSampleSize(
        actualW: Int, actualH: Int,
        reqW: Int, reqH: Int
    ): Int {
        var size = 1
        if (actualH > reqH || actualW > reqW) {
            val halfH = actualH / 2
            val halfW = actualW / 2
            while (halfH / size >= reqH && halfW / size >= reqW) {
                size *= 2
            }
        }
        return size
    }

    private fun readDiskCache(key: String): ByteArray? {
        val file = File(diskCacheDir, key)
        return if (file.exists() && file.length() > 0) {
            runCatching { file.readBytes() }.getOrNull()
        } else null
    }

    private fun writeDiskCache(key: String, data: ByteArray) {
        runCatching { File(diskCacheDir, key).writeBytes(data) }
            .onFailure { Log.w(TAG, "Disk cache write failed for key=$key", it) }
    }
}
