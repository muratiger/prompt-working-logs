package com.github.muratiger.promptworkinglogs.toolwindow

import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class ClaudeConsoleService {

    @Volatile
    var console: ConsoleView? = null

    @Volatile
    var processHandler: KillableColoredProcessHandler? = null

    @Volatile
    var latestResultFile: VirtualFile? = null
        private set

    private val resultFileListeners = CopyOnWriteArrayList<(VirtualFile?) -> Unit>()
    private val showConsoleListeners = CopyOnWriteArrayList<() -> Unit>()

    val isRunning: Boolean
        get() {
            val handler = processHandler ?: return false
            return !handler.isProcessTerminated && !handler.isProcessTerminating
        }

    fun stopProcess() {
        processHandler?.killProcess()
    }

    fun setLatestResultFile(file: VirtualFile?) {
        latestResultFile = file
        // CopyOnWriteArrayList's snapshot iterator ignores newly added elements, so this is safe.
        resultFileListeners.forEach { it.invoke(file) }
    }

    fun addResultFileListener(listener: (VirtualFile?) -> Unit) {
        resultFileListeners.add(listener)
    }

    fun removeResultFileListener(listener: (VirtualFile?) -> Unit) {
        resultFileListeners.remove(listener)
    }

    /** Requests subscribers to bring the execution log view to the front. */
    fun requestShowConsole() {
        showConsoleListeners.forEach { it.invoke() }
    }

    fun addShowConsoleListener(listener: () -> Unit) {
        showConsoleListeners.add(listener)
    }

    fun removeShowConsoleListener(listener: () -> Unit) {
        showConsoleListeners.remove(listener)
    }

    companion object {
        fun getInstance(project: Project): ClaudeConsoleService {
            return project.getService(ClaudeConsoleService::class.java)
        }
    }
}
