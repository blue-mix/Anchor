//// app/src/main/java/com/example/anchor/server/ContentDirectoryService.kt
//
//package com.example.anchor.server
//
//import android.util.Log
//import com.example.anchor.core.util.MimeTypeUtils
//import java.io.File
//import java.net.URLEncoder
//
///**
// * Handles UPnP ContentDirectory service SOAP requests.
// * This allows DLNA clients to browse the media library.
// */
//class ContentDirectoryService(
//    private val sharedDirectories: Map<String, File>,
//    private val serverBaseUrl: String
//) {
//    companion object {
//        private const val TAG = "ContentDirectoryService"
//    }
//
//    private var systemUpdateId = 1
//
//    /**
//     * Handles a SOAP Browse action.
//     */
//    fun handleBrowse(
//        objectId: String,
//        browseFlag: String,
//        filter: String,
//        startingIndex: Int,
//        requestedCount: Int,
//        sortCriteria: String
//    ): BrowseResult {
//        Log.d(TAG, "Browse: objectId=$objectId, flag=$browseFlag, start=$startingIndex, count=$requestedCount")
//
//        return when {
//            objectId == "0" -> browseRoot(startingIndex, requestedCount)
//            objectId.startsWith("dir:") -> browseDirectory(objectId, startingIndex, requestedCount)
//            else -> BrowseResult("", 0, 0, systemUpdateId)
//        }
//    }
//
//    /**
//     * Browses the root container (lists all shared directories).
//     */
//    private fun browseRoot(startingIndex: Int, requestedCount: Int): BrowseResult {
//        val items = StringBuilder()
//        val directories = sharedDirectories.entries.toList()
//
//        val count = if (requestedCount == 0) directories.size else requestedCount
//        val endIndex = minOf(startingIndex + count, directories.size)
//
//        for (i in startingIndex until endIndex) {
//            val (alias, dir) = directories[i]
//            items.append(buildContainerDidl(
//                id = "dir:$alias",
//                parentId = "0",
//                title = dir.name,
//                childCount = dir.listFiles()?.size ?: 0
//            ))
//        }
//
//        val didl = wrapDidl(items.toString())
//        return BrowseResult(didl, endIndex - startingIndex, directories.size, systemUpdateId)
//    }
//
//    /**
//     * Browses a specific directory.
//     */
//    private fun browseDirectory(objectId: String, startingIndex: Int, requestedCount: Int): BrowseResult {
//        val path = objectId.removePrefix("dir:")
//        val parts = path.split("/", limit = 2)
//        val alias = parts[0]
//        val relativePath = parts.getOrElse(1) { "" }
//
//        val baseDir = sharedDirectories[alias] ?: return BrowseResult("", 0, 0, systemUpdateId)
//        val targetDir = if (relativePath.isEmpty()) baseDir else File(baseDir, relativePath)
//
//        if (!targetDir.exists() || !targetDir.isDirectory) {
//            return BrowseResult("", 0, 0, systemUpdateId)
//        }
//
//        val files = targetDir.listFiles()
//            ?.filter { !it.isHidden }
//            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
//            ?: emptyList()
//
//        val items = StringBuilder()
//        val count = if (requestedCount == 0) files.size else requestedCount
//        val endIndex = minOf(startingIndex + count, files.size)
//
//        for (i in startingIndex until endIndex) {
//            val file = files[i]
//            val fileRelativePath = file.relativeTo(baseDir).path
//            val itemId = "dir:$alias/$fileRelativePath"
//
//            if (file.isDirectory) {
//                items.append(buildContainerDidl(
//                    id = itemId,
//                    parentId = objectId,
//                    title = file.name,
//                    childCount = file.listFiles()?.size ?: 0
//                ))
//            } else {
//                val mimeType = MimeTypeUtils.getMimeType(file.name)
//                val mediaClass = when {
//                    MimeTypeUtils.isVideo(mimeType) -> "object.item.videoItem"
//                    MimeTypeUtils.isAudio(mimeType) -> "object.item.audioItem.musicTrack"
//                    MimeTypeUtils.isImage(mimeType) -> "object.item.imageItem.photo"
//                    else -> "object.item"
//                }
//
//                val encodedPath = URLEncoder.encode("$alias/$fileRelativePath", "UTF-8")
//                    .replace("+", "%20")
//                val fileUrl = "$serverBaseUrl/files/$alias/$fileRelativePath"
//
//                items.append(buildItemDidl(
//                    id = itemId,
//                    parentId = objectId,
//                    title = file.nameWithoutExtension,
//                    mediaClass = mediaClass,
//                    mimeType = mimeType,
//                    url = fileUrl,
//                    size = file.length()
//                ))
//            }
//        }
//
//        val didl = wrapDidl(items.toString())
//        return BrowseResult(didl, endIndex - startingIndex, files.size, systemUpdateId)
//    }
//
//    /**
//     * Builds DIDL-Lite XML for a container (folder).
//     */
//    private fun buildContainerDidl(
//        id: String,
//        parentId: String,
//        title: String,
//        childCount: Int
//    ): String {
//        val escapedTitle = escapeXml(title)
//        return """
//            <container id="$id" parentID="$parentId" restricted="1" childCount="$childCount">
//                <dc:title>$escapedTitle</dc:title>
//                <upnp:class>object.container.storageFolder</upnp:class>
//            </container>
//        """.trimIndent()
//    }
//
//    /**
//     * Builds DIDL-Lite XML for a media item.
//     */
//    private fun buildItemDidl(
//        id: String,
//        parentId: String,
//        title: String,
//        mediaClass: String,
//        mimeType: String,
//        url: String,
//        size: Long
//    ): String {
//        val escapedTitle = escapeXml(title)
//        val escapedUrl = escapeXml(url)
//
//        return """
//            <item id="$id" parentID="$parentId" restricted="1">
//                <dc:title>$escapedTitle</dc:title>
//                <upnp:class>$mediaClass</upnp:class>
//                <res protocolInfo="http-get:*:$mimeType:*" size="$size">$escapedUrl</res>
//            </item>
//        """.trimIndent()
//    }
//
//    /**
//     * Wraps items in DIDL-Lite container.
//     */
//    private fun wrapDidl(items: String): String {
//        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">$items</DIDL-Lite>"""
//    }
//
//    /**
//     * Escapes XML special characters.
//     */
//    private fun escapeXml(text: String): String {
//        return text
//            .replace("&", "&amp;")
//            .replace("<", "&lt;")
//            .replace(">", "&gt;")
//            .replace("\"", "&quot;")
//            .replace("'", "&apos;")
//    }
//
//    /**
//     * Returns the current system update ID.
//     */
//    fun getSystemUpdateId(): Int = systemUpdateId
//
//    /**
//     * Increments the system update ID (call when content changes).
//     */
//    fun incrementUpdateId() {
//        systemUpdateId++
//    }
//
//    data class BrowseResult(
//        val result: String,
//        val numberReturned: Int,
//        val totalMatches: Int,
//        val updateId: Int
//    )
//}
// app/src/main/java/com/example/anchor/server/ContentDirectoryService.kt

package com.example.anchor.server

import android.util.Log
import com.example.anchor.core.util.MimeTypeUtils
import java.io.File
import java.net.URLEncoder

/**
 * Handles UPnP ContentDirectory service SOAP requests.
 */
class ContentDirectoryService(
    private val serverBaseUrl: String
) {
    companion object {
        private const val TAG = "ContentDirectoryService"
    }

    // Shared directories - will be updated from server
    private var sharedDirectories: Map<String, File> = emptyMap()

    private var systemUpdateId = 1

    /**
     * Updates the shared directories.
     */
    fun updateSharedDirectories(directories: Map<String, File>) {
        sharedDirectories = directories
        systemUpdateId++
    }

    /**
     * Handles a SOAP Browse action.
     */
    fun handleBrowse(
        objectId: String,
        browseFlag: String,
        filter: String,
        startingIndex: Int,
        requestedCount: Int,
        sortCriteria: String
    ): BrowseResult {
        Log.d(
            TAG,
            "Browse: objectId='$objectId', flag=$browseFlag, start=$startingIndex, count=$requestedCount"
        )
        Log.d(TAG, "Shared directories: ${sharedDirectories.keys}")

        return try {
            when {
                objectId == "0" -> browseRoot(startingIndex, requestedCount)
                objectId.startsWith("dir:") -> browseDirectory(
                    objectId,
                    startingIndex,
                    requestedCount
                )

                else -> {
                    Log.w(TAG, "Unknown objectId: $objectId")
                    BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Browse failed", e)
            BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
        }
    }

    /**
     * Browses the root container (lists all shared directories).
     */
    private fun browseRoot(startingIndex: Int, requestedCount: Int): BrowseResult {
        Log.d(TAG, "Browsing root, directories: ${sharedDirectories.size}")

        val items = StringBuilder()
        val directories = sharedDirectories.entries.toList()

        val count = if (requestedCount == 0) directories.size else requestedCount
        val endIndex = minOf(startingIndex + count, directories.size)

        for (i in startingIndex until endIndex) {
            val (alias, dir) = directories[i]
            val childCount = dir.listFiles()?.filter { !it.isHidden }?.size ?: 0

            Log.d(TAG, "Adding root container: $alias -> ${dir.name}, children: $childCount")

            items.append(
                buildContainerDidl(
                    id = "dir:$alias",
                    parentId = "0",
                    title = dir.name,
                    childCount = childCount
                )
            )
        }

        val didl = wrapDidl(items.toString())
        Log.d(TAG, "Root browse result: ${endIndex - startingIndex} items of ${directories.size}")

        return BrowseResult(didl, endIndex - startingIndex, directories.size, systemUpdateId)
    }

    /**
     * Browses a specific directory.
     */
    private fun browseDirectory(
        objectId: String,
        startingIndex: Int,
        requestedCount: Int
    ): BrowseResult {
        val path = objectId.removePrefix("dir:")
        val parts = path.split("/", limit = 2)
        val alias = parts[0]
        val relativePath = if (parts.size > 1) parts[1] else ""

        Log.d(TAG, "Browsing directory: alias='$alias', relativePath='$relativePath'")

        val baseDir = sharedDirectories[alias]
        if (baseDir == null) {
            Log.e(TAG, "Alias not found: $alias, available: ${sharedDirectories.keys}")
            return BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
        }

        val targetDir = if (relativePath.isEmpty()) baseDir else File(baseDir, relativePath)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            Log.e(TAG, "Directory not found: ${targetDir.absolutePath}")
            return BrowseResult(wrapDidl(""), 0, 0, systemUpdateId)
        }

        val files = targetDir.listFiles()
            ?.filter { !it.isHidden }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        Log.d(TAG, "Found ${files.size} files in ${targetDir.absolutePath}")

        val items = StringBuilder()
        val count = if (requestedCount == 0) files.size else requestedCount
        val endIndex = minOf(startingIndex + count, files.size)

        for (i in startingIndex until endIndex) {
            val file = files[i]
            val fileRelativePath = file.relativeTo(baseDir).path.replace("\\", "/")
            val itemId = "dir:$alias/$fileRelativePath"

            if (file.isDirectory) {
                val childCount = file.listFiles()?.filter { !it.isHidden }?.size ?: 0
                items.append(
                    buildContainerDidl(
                        id = itemId,
                        parentId = objectId,
                        title = file.name,
                        childCount = childCount
                    )
                )
            } else {
                val mimeType = MimeTypeUtils.getMimeType(file.name)
                val mediaClass = when {
                    MimeTypeUtils.isVideo(mimeType) -> "object.item.videoItem"
                    MimeTypeUtils.isAudio(mimeType) -> "object.item.audioItem.musicTrack"
                    MimeTypeUtils.isImage(mimeType) -> "object.item.imageItem.photo"
                    else -> "object.item"
                }

                // Build the file URL with proper encoding
                val encodedPath = fileRelativePath.split("/").joinToString("/") { segment ->
                    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                }
                val fileUrl = "$serverBaseUrl/files/$alias/$encodedPath"

                Log.d(TAG, "Adding item: ${file.name} -> $fileUrl")

                items.append(
                    buildItemDidl(
                        id = itemId,
                        parentId = objectId,
                        title = file.nameWithoutExtension,
                        mediaClass = mediaClass,
                        mimeType = mimeType,
                        url = fileUrl,
                        size = file.length()
                    )
                )
            }
        }

        val didl = wrapDidl(items.toString())
        return BrowseResult(didl, endIndex - startingIndex, files.size, systemUpdateId)
    }

    /**
     * Builds DIDL-Lite XML for a container (folder).
     */
    private fun buildContainerDidl(
        id: String,
        parentId: String,
        title: String,
        childCount: Int
    ): String {
        val escapedId = escapeXml(id)
        val escapedParentId = escapeXml(parentId)
        val escapedTitle = escapeXml(title)

        return """<container id="$escapedId" parentID="$escapedParentId" restricted="1" searchable="0" childCount="$childCount">
<dc:title>$escapedTitle</dc:title>
<upnp:class>object.container.storageFolder</upnp:class>
</container>"""
    }

    /**
     * Builds DIDL-Lite XML for a media item.
     */
    private fun buildItemDidl(
        id: String,
        parentId: String,
        title: String,
        mediaClass: String,
        mimeType: String,
        url: String,
        size: Long
    ): String {
        val escapedId = escapeXml(id)
        val escapedParentId = escapeXml(parentId)
        val escapedTitle = escapeXml(title)
        val escapedUrl = escapeXml(url)

        // Build protocol info based on media type
        val dlnaFeatures = when {
            mimeType.startsWith("video/") -> "DLNA.ORG_PN=MATROSKA;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            mimeType.startsWith("audio/") -> "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
            mimeType.startsWith("image/") -> "DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01;DLNA.ORG_FLAGS=00900000000000000000000000000000"
            else -> "*"
        }

        val protocolInfo = "http-get:*:$mimeType:$dlnaFeatures"

        return """<item id="$escapedId" parentID="$escapedParentId" restricted="1">
<dc:title>$escapedTitle</dc:title>
<upnp:class>$mediaClass</upnp:class>
<res protocolInfo="$protocolInfo" size="$size">$escapedUrl</res>
</item>"""
    }

    /**
     * Wraps items in DIDL-Lite container.
     */
    private fun wrapDidl(items: String): String {
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">$items</DIDL-Lite>"""
    }

    /**
     * Escapes XML special characters.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun getSystemUpdateId(): Int = systemUpdateId

    fun incrementUpdateId() {
        systemUpdateId++
    }

    data class BrowseResult(
        val result: String,
        val numberReturned: Int,
        val totalMatches: Int,
        val updateId: Int
    )
}