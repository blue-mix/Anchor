package com.example.anchor.server.dlna

import android.util.Log
import com.example.anchor.data.mapper.MediaFileMapper.filesToDirectoryListing
import com.example.anchor.domain.model.MediaItem
import com.example.anchor.domain.model.MediaType
import java.io.File
import java.net.URLEncoder

/**
 * Handles UPnP ContentDirectory SOAP Browse requests.
 */
class ContentDirectoryService(private val serverBaseUrl: String) {

    companion object {
        private const val TAG = "ContentDirectoryService"
        
        private const val ROOT_ID = "0"
        private const val DIR_PREFIX = "dir:"
    }

    private var sharedDirectories: Map<String, File> = emptyMap()
    private var systemUpdateId = 1

    fun updateSharedDirectories(directories: Map<String, File>) {
        sharedDirectories = directories
        systemUpdateId++
        Log.d(TAG, "Directories updated: ${directories.keys}")
    }

    fun getSystemUpdateId(): Int = systemUpdateId
    
    fun handleBrowse(
        objectId: String,
        browseFlag: String,
        filter: String,
        startingIndex: Int,
        requestedCount: Int,
        sortCriteria: String
    ): BrowseResult = try {
        when {
            objectId == ROOT_ID -> browseRoot(startingIndex, requestedCount)
            objectId.startsWith(DIR_PREFIX) -> browseDirectory(objectId, startingIndex, requestedCount)
            else -> {
                Log.w(TAG, "Unknown objectId: $objectId")
                BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Browse failed", e)
        BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
    }

    private fun browseRoot(startingIndex: Int, requestedCount: Int): BrowseResult {
        val entries = sharedDirectories.entries.toList()
        val total = entries.size
        val count = if (requestedCount <= 0) total else requestedCount
        val end = minOf(startingIndex + count, total)

        val items = buildString {
            for (i in startingIndex until end) {
                val (alias, dir) = entries[i]
                val childCount = dir.listFiles()?.count { !it.name.startsWith(".") } ?: 0
                append(containerDidl(
                    id = "$DIR_PREFIX$alias",
                    parentId = ROOT_ID,
                    title = dir.name,
                    childCount = childCount
                ))
            }
        }

        return BrowseResult(wrapDidl(items), end - startingIndex, total, systemUpdateId)
    }

    private fun browseDirectory(
        objectId: String,
        startingIndex: Int,
        requestedCount: Int
    ): BrowseResult {
        val path = objectId.removePrefix(DIR_PREFIX)
        val parts = path.split("/", limit = 2)
        val alias = parts[0]
        val relativePath = parts.getOrNull(1) ?: ""

        val baseDir = sharedDirectories[alias] ?: return BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
        val targetDir = if (relativePath.isEmpty()) baseDir else File(baseDir, relativePath)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            return BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
        }

        val listing = filesToDirectoryListing(baseDir, alias, targetDir)
        val allItems = listing.items
        val total = allItems.size
        val count = if (requestedCount <= 0) total else requestedCount
        val end = minOf(startingIndex + count, total)

        val didlItems = buildString {
            for (i in startingIndex until end) {
                val item = allItems[i]
                val itemId = "$DIR_PREFIX${item.path.value.removePrefix("/")}"
                if (item.isDirectory) {
                    val childCount = File(item.absolutePath).listFiles()?.count { !it.name.startsWith(".") } ?: 0
                    append(containerDidl(itemId, objectId, item.name, childCount))
                } else {
                    append(itemDidl(item, objectId, itemId))
                }
            }
        }

        return BrowseResult(wrapDidl(didlItems), end - startingIndex, total, systemUpdateId)
    }

    private fun containerDidl(id: String, parentId: String, title: String, childCount: Int) =
        """<container id="${escXml(id)}" parentID="${escXml(parentId)}" restricted="1" searchable="0" childCount="$childCount">
<dc:title>${escXml(title)}</dc:title>
<upnp:class>object.container.storageFolder</upnp:class>
</container>"""

    private fun itemDidl(item: MediaItem, parentId: String, itemId: String): String {
        val mediaClass = when (item.mediaType) {
            MediaType.VIDEO -> "object.item.videoItem"
            MediaType.AUDIO -> "object.item.audioItem.musicTrack"
            MediaType.IMAGE -> "object.item.imageItem.photo"
            else -> "object.item"
        }

        val fileUrl = "$serverBaseUrl/files/${item.encodedPath.trimStart('/')}"

        val dlnaPn = when (item.mimeType.value) {
            "video/mp4" -> "AVC_MP4_MP_SD_AAC_MULTICHANNEL"
            "video/x-matroska" -> "MATROSKA"
            "video/mpeg" -> "MPEG_PS_PAL"
            "audio/mpeg" -> "MP3"
            "audio/wav" -> "LPCM"
            "image/jpeg" -> "JPEG_LRG"
            "image/png" -> "PNG_LRG"
            else -> null
        }

        val dlnaFeatures = if (dlnaPn != null) {
            "DLNA.ORG_PN=$dlnaPn;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        } else {
            "DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        }

        val protocolInfo = "http-get:*:${item.mimeType.value}:$dlnaFeatures"

        return """<item id="${escXml(itemId)}" parentID="${escXml(parentId)}" restricted="1">
<dc:title>${escXml(item.name.substringBeforeLast('.'))}</dc:title>
<upnp:class>$mediaClass</upnp:class>
<res protocolInfo="${escXml(protocolInfo)}" size="${item.size}">${escXml(fileUrl)}</res>
</item>"""
    }

    private fun wrapDidl(items: String): String = buildString {
        append("<DIDL-Lite ")
        append("xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
        append("xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">")
        append(items)
        append("</DIDL-Lite>")
    }

    private fun escXml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    data class BrowseResult(
        val result: String,
        val numberReturned: Int,
        val totalMatches: Int,
        val updateId: Int
    )
}
