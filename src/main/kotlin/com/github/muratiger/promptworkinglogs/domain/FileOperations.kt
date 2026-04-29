package com.github.muratiger.promptworkinglogs.domain

import java.io.File

/**
 * Abstracts file/directory operations on the prompt-files tree. To extract the
 * Action classes out of inner classes, this interface centralizes the UI layer's
 * direct dependency on VirtualFileSystem. Error UI is handled by the caller
 * (Action); this interface only returns the result as [FileOperationResult].
 */
interface FileOperations {
    fun createFile(parentDir: File, name: String, initialContent: String? = null): FileOperationResult
    fun createDirectory(parentDir: File, name: String): FileOperationResult
    fun rename(target: File, newName: String): FileOperationResult
    fun moveTo(target: File, destinationDir: File): FileOperationResult
    fun delete(target: File): FileOperationResult
}
