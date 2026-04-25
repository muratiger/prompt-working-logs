package com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions

import com.github.muratiger.promptworkinglogs.domain.FileOperationResult
import com.github.muratiger.promptworkinglogs.domain.FileOperations
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.FileNameInputValidator
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.PromptFilesController
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

internal class NewDirectoryAction(
    private val controller: PromptFilesController,
    private val fileOps: FileOperations,
) : AnAction(
    "New Directory",
    "Create a new directory in the selected directory (or root when nothing is selected)",
    AllIcons.Nodes.Folder
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = controller.targetDirectoryForCreation() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dir = controller.targetDirectoryForCreation() ?: return
        val name = Messages.showInputDialog(
            controller.project,
            "Enter directory name",
            "New Directory",
            AllIcons.Nodes.Folder,
            "",
            FileNameInputValidator(dir)
        )?.trim().orEmpty()
        if (name.isEmpty()) return

        when (val result = fileOps.createDirectory(dir, name)) {
            is FileOperationResult.Success -> controller.refreshAndSelect(result.resultPath)
            is FileOperationResult.Failure -> Messages.showErrorDialog(controller.project, result.message, "New Directory")
        }
    }
}
