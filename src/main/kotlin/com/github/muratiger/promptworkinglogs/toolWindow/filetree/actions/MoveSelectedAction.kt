package com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions

import com.github.muratiger.promptworkinglogs.domain.FileOperationResult
import com.github.muratiger.promptworkinglogs.domain.FileOperations
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.PromptFilesController
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.File

internal class MoveSelectedAction(
    private val controller: PromptFilesController,
    private val fileOps: FileOperations,
) : AnAction(
    "Move",
    "Move the selected file or directory to another directory",
    AllIcons.Actions.Forward
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selected = controller.selectedFile()
        val root = controller.rootFile()
        e.presentation.isEnabled = selected != null && selected != root
    }

    @Suppress("DEPRECATION")
    override fun actionPerformed(e: AnActionEvent) {
        val selected = controller.selectedFile() ?: return
        val root = controller.rootFile() ?: return
        if (selected == root) return
        val currentParent = selected.parentFile ?: return

        val candidates = collectMoveTargetDirectories(root, selected)
            .filter { it != currentParent }
        if (candidates.isEmpty()) {
            Messages.showInfoMessage(
                controller.project,
                "No other destination directory available",
                "Move"
            )
            return
        }

        val rootPath = root.toPath()
        val labels = candidates.map { dir ->
            val rel = rootPath.relativize(dir.toPath()).toString()
            if (rel.isEmpty()) "/ (${root.name})" else rel
        }.toTypedArray()

        val idx = Messages.showChooseDialog(
            controller.project,
            "Select destination directory",
            "Move: ${selected.name}",
            AllIcons.Actions.Forward,
            labels,
            labels.first()
        )
        if (idx < 0) return
        val targetDir = candidates[idx]

        when (val result = fileOps.moveTo(selected, targetDir)) {
            is FileOperationResult.Success -> controller.refreshAndSelect(result.resultPath)
            is FileOperationResult.Failure -> Messages.showErrorDialog(controller.project, result.message, "Move")
        }
    }

    private fun collectMoveTargetDirectories(root: File, exclude: File): List<File> {
        val result = mutableListOf<File>()
        fun walk(dir: File) {
            result.add(dir)
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory && child != exclude) {
                    walk(child)
                }
            }
        }
        if (root.isDirectory) walk(root)
        return result
    }
}
