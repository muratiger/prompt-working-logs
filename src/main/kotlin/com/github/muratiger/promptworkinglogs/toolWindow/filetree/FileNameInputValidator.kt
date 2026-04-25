package com.github.muratiger.promptworkinglogs.toolwindow.filetree

import com.intellij.openapi.ui.InputValidator
import java.io.File

/**
 * Validates a file/directory name typed into a creation/rename dialog. The candidate must be
 * non-empty, free of path separators, not "." or "..", and must not collide with an existing
 * sibling (the optional [ignoreExisting] lets the rename dialog accept the unchanged name).
 */
internal class FileNameInputValidator(
    private val parentDir: File,
    private val ignoreExisting: File? = null
) : InputValidator {

    override fun checkInput(inputString: String?): Boolean {
        val name = inputString?.trim().orEmpty()
        if (name.isEmpty()) return false
        if (name.contains('/') || name.contains('\\')) return false
        if (name == "." || name == "..") return false
        val candidate = File(parentDir, name)
        if (candidate.exists() && candidate != ignoreExisting) return false
        return true
    }

    override fun canClose(inputString: String?): Boolean = checkInput(inputString)
}
