package com.github.muratiger.promptworkinglogs.toolwindow

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ClaudeConsoleService {
    var console: ConsoleView? = null

    companion object {
        fun getInstance(project: Project): ClaudeConsoleService {
            return project.getService(ClaudeConsoleService::class.java)
        }
    }
}
