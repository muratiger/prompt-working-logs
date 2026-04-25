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

internal class RenameSelectedAction(
    private val controller: PromptFilesController,
    private val fileOps: FileOperations,
) : AnAction(
    "Rename",
    "Rename the selected file or directory",
    AllIcons.Actions.Edit
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
        val parent = selected.parentFile ?: return

        val newName = Messages.showInputDialog(
            controller.project,
            "Enter new name",
            "Rename",
            AllIcons.Actions.Edit,
            selected.name,
            FileNameInputValidator(parent, selected)
        )?.trim().orEmpty()
        if (newName.isEmpty() || newName == selected.name) return

        when (val result = fileOps.rename(selected, newName)) {
            is FileOperationResult.Success -> controller.refreshAndSelect(result.resultPath)
            is FileOperationResult.Failure -> Messages.showErrorDialog(controller.project, result.message, "Rename")
        }
    }
}
