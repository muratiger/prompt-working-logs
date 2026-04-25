package com.github.muratiger.promptworkinglogs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPathsTest {

    @Test
    fun `relativeFilePath returns path beneath base`() {
        val rel = ProjectPaths.relativeFilePath("/home/user/project", "/home/user/project/prompts/test.md")
        assertEquals("prompts/test.md", rel)
    }

    @Test
    fun `relativeFilePath returns null for sibling directory`() {
        // Plain string startsWith would mistakenly accept this; NIO relativize must reject it.
        val rel = ProjectPaths.relativeFilePath("/home/user/project", "/home/user/project-other/test.md")
        assertNull(rel)
    }

    @Test
    fun `relativeFilePath returns null when target is outside base`() {
        val rel = ProjectPaths.relativeFilePath("/home/user/project", "/tmp/test.md")
        assertNull(rel)
    }

    @Test
    fun `isUnderWatchedDir matches exact directory and descendants`() {
        assertTrue(ProjectPaths.isUnderWatchedDir("prompts", "prompts"))
        assertTrue(ProjectPaths.isUnderWatchedDir("prompts/test.md", "prompts"))
        assertTrue(ProjectPaths.isUnderWatchedDir("prompts/sub/test.md", "prompts"))
    }

    @Test
    fun `isUnderWatchedDir rejects sibling prefixed names`() {
        assertFalse(ProjectPaths.isUnderWatchedDir("prompts-archive/test.md", "prompts"))
        assertFalse(ProjectPaths.isUnderWatchedDir("promptsX/test.md", "prompts"))
    }

    @Test
    fun `isUnderWatchedDir tolerates trailing slashes`() {
        assertTrue(ProjectPaths.isUnderWatchedDir("prompts/test.md", "prompts/"))
        assertTrue(ProjectPaths.isUnderWatchedDir("prompts/test.md", "/prompts/"))
    }

    @Test
    fun `isUnderWatchedDir returns true for empty watched dir (project root)`() {
        assertTrue(ProjectPaths.isUnderWatchedDir("anything/test.md", ""))
    }
}
