package com.github.muratiger.promptworkinglogs.toolwindow.filetree

import com.github.muratiger.promptworkinglogs.domain.FileOperationResult
import com.github.muratiger.promptworkinglogs.domain.FileOperations
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Default [FileOperations] implementation backed by the VirtualFileSystem.
 * Executes under WriteCommandAction to preserve undo and IDE index consistency.
 * Passes this instance as the `requestor` argument to VFS APIs so the change
 * source is identifiable.
 */
internal class VfsFileOperations(private val project: Project) : FileOperations {

    override fun createFile(parentDir: File, name: String, initialContent: String?): FileOperationResult {
        val parentVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
            ?: return FileOperationResult.Failure("Parent directory not available: ${parentDir.path}")
        return try {
            val created = WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
                val file = parentVf.createChildData(this, name)
                if (initialContent != null) {
                    file.setBinaryContent(initialContent.toByteArray(Charsets.UTF_8))
                }
                file
            }
            FileOperationResult.Success(created.path)
        } catch (ex: Exception) {
            FileOperationResult.Failure("Failed to create file: ${ex.message}")
        }
    }

    override fun createDirectory(parentDir: File, name: String): FileOperationResult {
        val parentVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
            ?: return FileOperationResult.Failure("Parent directory not available: ${parentDir.path}")
        return try {
            val created = WriteCommandAction.runWriteCommandAction<VirtualFile?>(project) {
                VfsUtil.createDirectoryIfMissing(parentVf, name)
            } ?: return FileOperationResult.Failure("Failed to create directory")
            FileOperationResult.Success(created.path)
        } catch (ex: Exception) {
            FileOperationResult.Failure("Failed to create directory: ${ex.message}")
        }
    }

    override fun rename(target: File, newName: String): FileOperationResult {
        val parent = target.parentFile
            ?: return FileOperationResult.Failure("File has no parent: ${target.path}")
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
            ?: return FileOperationResult.Failure("File not found in VFS: ${target.path}")
        val newPath = File(parent, newName).absolutePath
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                vf.rename(this, newName)
            }
            FileOperationResult.Success(newPath)
        } catch (ex: Exception) {
            FileOperationResult.Failure("Failed to rename: ${ex.message}")
        }
    }

    override fun moveTo(target: File, destinationDir: File): FileOperationResult {
        val destFile = File(destinationDir, target.name)
        if (destFile.exists()) {
            val kind = if (target.isDirectory) "directory" else "file"
            return FileOperationResult.Failure(
                "A $kind with the same name already exists at destination: ${destFile.path}"
            )
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
            ?: return FileOperationResult.Failure("Source not found in VFS: ${target.path}")
        val destVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destinationDir)
            ?: return FileOperationResult.Failure("Destination not found in VFS: ${destinationDir.path}")
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                vf.move(this, destVf)
            }
            FileOperationResult.Success(destFile.absolutePath)
        } catch (ex: Exception) {
            FileOperationResult.Failure("Failed to move: ${ex.message}")
        }
    }

    override fun delete(target: File): FileOperationResult {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
            ?: return FileOperationResult.Failure("File not found in VFS: ${target.path}")
        val parentPath = target.parentFile?.absolutePath ?: target.absolutePath
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                vf.delete(this)
            }
            FileOperationResult.Success(parentPath)
        } catch (ex: Exception) {
            FileOperationResult.Failure("Failed to delete: ${ex.message}")
        }
    }
}
