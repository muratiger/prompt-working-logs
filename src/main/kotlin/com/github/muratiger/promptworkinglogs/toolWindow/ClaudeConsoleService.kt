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
    private val runStateListeners = CopyOnWriteArrayList<RunStateListener>()

    @Volatile
    var runStartTimeMillis: Long? = null
        private set

    @Volatile
    var lastFinishedElapsedMillis: Long? = null
        private set

    interface RunStateListener {
        fun onRunStarted(startTimeMillis: Long)
        fun onRunFinished(elapsedMillis: Long)
    }

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

    fun notifyRunStarted() {
        val now = System.currentTimeMillis()
        runStartTimeMillis = now
        lastFinishedElapsedMillis = null
        runStateListeners.forEach { it.onRunStarted(now) }
    }

    fun notifyRunFinished() {
        val start = runStartTimeMillis ?: return
        val elapsed = System.currentTimeMillis() - start
        lastFinishedElapsedMillis = elapsed
        runStartTimeMillis = null
        runStateListeners.forEach { it.onRunFinished(elapsed) }
    }

    fun addRunStateListener(listener: RunStateListener) {
        runStateListeners.add(listener)
    }

    fun removeRunStateListener(listener: RunStateListener) {
        runStateListeners.remove(listener)
    }

    companion object {
        fun getInstance(project: Project): ClaudeConsoleService {
            return project.getService(ClaudeConsoleService::class.java)
        }
    }
}
