package com.github.muratiger.promptworkinglogs.action

import com.github.muratiger.promptworkinglogs.runner.ClaudeCliRunner
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.github.muratiger.promptworkinglogs.util.ProjectPaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RunClaudeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!isEligible(project, file)) return
        ClaudeCliRunner.run(project, file)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible =
            project != null && file != null && isEligible(project, file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        const val ID: String = "com.github.muratiger.promptworkinglogs.RunClaudeAction"

        fun isEligible(project: Project, file: VirtualFile): Boolean {
            if (!file.name.endsWith(".md")) return false
            val basePath = project.basePath ?: return false
            val watchedDir = SimpleSettings.getInstance().state.watchedDirectory
            val relativePath = ProjectPaths.relativeFilePath(basePath, file) ?: return false
            return ProjectPaths.isUnderWatchedDir(relativePath, watchedDir)
        }
    }
}
