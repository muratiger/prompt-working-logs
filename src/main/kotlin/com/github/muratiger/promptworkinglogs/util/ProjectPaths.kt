package com.github.muratiger.promptworkinglogs.util

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object ProjectPaths {

    /**
     * Returns the path of [file] relative to [basePath] using NIO path semantics, or null when
     * [file] is not contained in [basePath]. The returned string always uses forward slashes so it
     * can be safely interpolated into shell commands or compared against forward-slash-prefixed
     * directory names.
     */
    fun relativeFilePath(basePath: String, file: VirtualFile): String? =
        relativeFilePath(basePath, file.path)

    fun relativeFilePath(basePath: String, absolutePath: String): String? {
        return try {
            val base = Paths.get(basePath).toAbsolutePath().normalize()
            val target = Paths.get(absolutePath).toAbsolutePath().normalize()
            if (!target.startsWith(base)) return null
            base.relativize(target).toString().replace('\\', '/')
        } catch (_: InvalidPathException) {
            null
        }
    }

    /**
     * True when [relativePath] equals [watchedDir] or sits beneath it. Plain `startsWith` would
     * incorrectly match siblings like `prompt-work-archive` when the watched directory is
     * `prompt-work`.
     */
    fun isUnderWatchedDir(relativePath: String, watchedDir: String): Boolean {
        if (watchedDir.isEmpty()) return true
        val normalizedWatched = watchedDir.trim('/').replace('\\', '/')
        if (normalizedWatched.isEmpty()) return true
        if (relativePath == normalizedWatched) return true
        return relativePath.startsWith("$normalizedWatched/")
    }
}
