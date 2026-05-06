package com.github.muratiger.promptworkinglogs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the minimal invariants of [FileOperationResult] / [FileOperations].
 * Operations involving real IO belong to integration tests on top of
 * BasePlatformTestCase; here we cover sealed-class sub-type detection and
 * the ease of substituting fake implementations.
 */
class FileOperationResultTest {

    @Test
    fun `success carries result path`() {
        val result: FileOperationResult = FileOperationResult.Success("/foo/bar")
        assertTrue(result is FileOperationResult.Success)
        when (result) {
            is FileOperationResult.Success -> assertEquals("/foo/bar", result.resultPath)
            is FileOperationResult.Failure -> error("expected Success but got Failure")
        }
    }

    @Test
    fun `failure carries message`() {
        val result: FileOperationResult = FileOperationResult.Failure("disk full")
        assertTrue(result is FileOperationResult.Failure)
        when (result) {
            is FileOperationResult.Success -> error("expected Failure but got Success")
            is FileOperationResult.Failure -> assertEquals("disk full", result.message)
        }
    }

    @Test
    fun `interface allows fake implementations for action tests`() {
        val fake = object : FileOperations {
            override fun createFile(parentDir: java.io.File, name: String, initialContent: String?): FileOperationResult =
                FileOperationResult.Success("${parentDir.path}/$name")
            override fun createDirectory(parentDir: java.io.File, name: String): FileOperationResult =
                FileOperationResult.Success("${parentDir.path}/$name")
            override fun rename(target: java.io.File, newName: String): FileOperationResult =
                FileOperationResult.Success("${target.parentFile?.path}/$newName")
            override fun moveTo(target: java.io.File, destinationDir: java.io.File): FileOperationResult =
                FileOperationResult.Success("${destinationDir.path}/${target.name}")
            override fun delete(target: java.io.File): FileOperationResult =
                FileOperationResult.Success(target.parentFile?.path ?: "")
        }

        val r = fake.createFile(java.io.File("/tmp"), "x.md")
        when (r) {
            is FileOperationResult.Success -> assertEquals("/tmp/x.md", r.resultPath)
            is FileOperationResult.Failure -> error("unexpected failure: ${r.message}")
        }
    }
}
