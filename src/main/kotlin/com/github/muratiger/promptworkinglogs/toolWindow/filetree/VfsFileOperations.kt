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
 * VirtualFileSystem ベースの [FileOperations] 既定実装。WriteCommandAction
 * 配下で実行することで undo / IDE インデックス整合性を確保する。
 * VFS API の `requestor` 引数には本インスタンスを渡し、変更元を識別可能にする。
 */
internal class VfsFileOperations(private val project: Project) : FileOperations {

    override fun createFile(parentDir: File, name: String): FileOperationResult {
        val parentVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
            ?: return FileOperationResult.Failure("Parent directory not available: ${parentDir.path}")
        return try {
            val created = WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
                parentVf.createChildData(this, name)
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
