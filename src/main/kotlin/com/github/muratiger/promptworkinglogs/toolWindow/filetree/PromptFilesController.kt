package com.github.muratiger.promptworkinglogs.toolwindow.filetree

import com.intellij.openapi.project.Project
import java.io.File

/**
 * API abstraction for the prompt-files tree panel. Exposes the minimal panel
 * state (selected file, root, redraw) required to extract the action classes
 * out of inner classes. The implementation is the panel inside
 * [com.github.muratiger.promptworkinglogs.toolwindow.PromptFilesToolWindowFactory].
 */
interface PromptFilesController {
    val project: Project

    /** The file/directory currently selected in the tree. Null when no selection. */
    fun selectedFile(): File?

    /** The tree's root directory (the watched directory). Null when it does not exist. */
    fun rootFile(): File?

    /** Target directory for new entries: the selection if it is a directory, its parent if a file, or the root when nothing is selected. */
    fun targetDirectoryForCreation(): File?

    /** Rebuilds the entire tree and selects the given path. */
    fun refreshAndSelect(absolutePath: String)

    /** Rebuilds the entire tree. */
    fun refreshTree()
}
