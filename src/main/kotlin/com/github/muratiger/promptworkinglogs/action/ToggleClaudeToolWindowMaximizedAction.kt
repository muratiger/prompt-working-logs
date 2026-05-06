package com.github.muratiger.promptworkinglogs.action

import com.github.muratiger.promptworkinglogs.toolwindow.ClaudeToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

class ToggleClaudeToolWindowMaximizedAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val log = thisLogger()
        val project = e.project
        if (project == null) {
            log.info("ToggleClaudeToolWindowMaximizedAction invoked but project is null")
            return
        }
        val manager = ToolWindowManager.getInstance(project)
        val toolWindow = manager.getToolWindow(ClaudeToolWindowFactory.TOOL_WINDOW_ID)
        if (toolWindow == null) {
            log.warn("ToggleClaudeToolWindowMaximizedAction: tool window '${ClaudeToolWindowFactory.TOOL_WINDOW_ID}' not found")
            return
        }
        val shouldMaximize = !manager.isMaximized(toolWindow)
        log.info("ToggleClaudeToolWindowMaximizedAction: visible=${toolWindow.isVisible}, maximized=${manager.isMaximized(toolWindow)}, shouldMaximize=$shouldMaximize")
        if (shouldMaximize && !toolWindow.isVisible) {
            toolWindow.show { manager.setMaximized(toolWindow, true) }
        } else {
            manager.setMaximized(toolWindow, shouldMaximize)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    companion object {
        const val ID: String = "com.github.muratiger.promptworkinglogs.ToggleClaudeToolWindowMaximizedAction"
    }
}
