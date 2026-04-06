package com.example.anchor.core.extension

import java.io.File

/**
 * Returns the file extension in lowercase, or an empty string if the file
 * has no extension (e.g., "README").
 */
val File.extension: String
    get() = name.substringAfterLast('.', "").lowercase()

/**
 * Returns the file name without its extension.
 */
val File.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')

/**
 * Returns true when this file is within [ancestor] — i.e., when this
 * file's canonical path starts with [ancestor]'s canonical path.
 * Use for path-traversal security checks.
 */
fun File.isDescendantOf(ancestor: File): Boolean =
    canonicalPath.startsWith(ancestor.canonicalPath + File.separator) ||
            canonicalPath == ancestor.canonicalPath

// ── Directory helpers ─────────────────────────────────────────

/**
 * Returns the total size of all regular files under this directory,
 * or length if this is a regular file.
 */
fun File.totalSize(): Long =
    if (isFile) length()
    else walkTopDown().filter { it.isFile }.sumOf { it.length() }

/**
 * Returns the count of regular (non-hidden) files directly inside this
 * directory, or 1 for a regular file.
 */
fun File.shallowFileCount(): Int =
    if (isFile) 1
    else listFiles()?.count { it.isFile && !it.isHidden } ?: 0

/**
 * Creates all missing parent directories and then this directory.
 * Equivalent to [File.mkdirs] but returns this [File] for chaining.
 */
fun File.ensureDirectories(): File = apply { mkdirs() }
