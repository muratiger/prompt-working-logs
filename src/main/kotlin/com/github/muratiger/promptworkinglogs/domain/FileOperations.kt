package com.github.muratiger.promptworkinglogs.domain

import java.io.File

/**
 * prompt-files ツリー上のファイル/ディレクトリ操作を抽象化する。Action クラスを
 * inner class から脱出させるため、UI 層から VirtualFileSystem への直接依存を
 * このインターフェース経由に集約する。エラー UI は呼び出し側 (Action) が担当し、
 * 本インターフェースは結果を [FileOperationResult] で返すだけにする。
 */
interface FileOperations {
    fun createFile(parentDir: File, name: String): FileOperationResult
    fun createDirectory(parentDir: File, name: String): FileOperationResult
    fun rename(target: File, newName: String): FileOperationResult
    fun moveTo(target: File, destinationDir: File): FileOperationResult
    fun delete(target: File): FileOperationResult
}
