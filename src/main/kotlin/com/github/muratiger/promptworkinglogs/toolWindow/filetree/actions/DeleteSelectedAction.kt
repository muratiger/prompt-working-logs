package com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions

import com.github.muratiger.promptworkinglogs.domain.FileOperationResult
import com.github.muratiger.promptworkinglogs.domain.FileOperations
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.PromptFilesController
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

internal class DeleteSelectedAction(
    private val controller: PromptFilesController,
    private val fileOps: FileOperations,
) : AnAction(
    "Delete",
    "Delete the selected file or directory",
    AllIcons.Actions.GC
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val selected = controller.selectedFile()
        val root = controller.rootFile()
        e.presentation.isEnabled = selected != null && selected != root
    }

    override fun actionPerformed(e: AnActionEvent) {
        val selected = controller.selectedFile() ?: return
        if (selected == controller.rootFile()) return

        val kind = if (selected.isDirectory) "Directory" else "File"
        val confirm = Messages.showYesNoDialog(
            controller.project,
            "Delete ${selected.name}?" + if (selected.isDirectory) "\n(Files within will also be deleted)" else "",
            "Delete $kind",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return

        when (val result = fileOps.delete(selected)) {
            is FileOperationResult.Success -> {
                if (result.resultPath.isEmpty()) {
                    controller.refreshTree()
                } else {
                    controller.refreshAndSelect(result.resultPath)
                }
            }
            is FileOperationResult.Failure -> Messages.showErrorDialog(controller.project, result.message, "Delete")
        }
    }
}
