package com.github.muratiger.promptworkinglogs.toolwindow

import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ClaudeConsoleService {
    var console: ConsoleView? = null
    var processHandler: KillableColoredProcessHandler? = null

    var latestResultFile: VirtualFile? = null
        private set

    private val resultFileListeners: MutableList<(VirtualFile?) -> Unit> = mutableListOf()

    val isRunning: Boolean
        get() = processHandler?.isProcessTerminated == false && processHandler?.isProcessTerminating == false

    fun stopProcess() {
        processHandler?.killProcess()
    }

    fun setLatestResultFile(file: VirtualFile?) {
        latestResultFile = file
        resultFileListeners.toList().forEach { it.invoke(file) }
    }

    fun addResultFileListener(listener: (VirtualFile?) -> Unit) {
        resultFileListeners.add(listener)
    }

    fun removeResultFileListener(listener: (VirtualFile?) -> Unit) {
        resultFileListeners.remove(listener)
    }

    companion object {
        fun getInstance(project: Project): ClaudeConsoleService {
            return project.getService(ClaudeConsoleService::class.java)
        }
    }
}
