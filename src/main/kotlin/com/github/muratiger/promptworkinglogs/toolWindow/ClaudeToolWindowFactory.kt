package com.github.muratiger.promptworkinglogs.toolwindow

import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.Timer

class ClaudeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ClaudeConsolePanel(project, toolWindow)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        Disposer.register(content) { panel.dispose() }
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID: String = "prompt-work"
    }
}

private const val CARD_CONSOLE = "console"
private const val CARD_RESULT = "result"
private const val MARKDOWN_PREVIEW_EDITOR_TYPE_ID = "markdown-preview-editor"
private const val MARKDOWN_SPLIT_EDITOR_TYPE_ID = "split-markdown-editor"

private class ClaudeConsolePanel(
    private val project: Project,
    toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    private val console: ConsoleView =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).console

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private val resultContainer = JPanel(BorderLayout())
    private val resultPlaceholder = JBLabel("No result MD yet", JBLabel.CENTER)

    private var currentResultFileEditor: FileEditor? = null

    private var showingResult = false

    private val service = ClaudeConsoleService.getInstance(project)

    private val elapsedTimeLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(2, 6)
    }
    private var elapsedTimer: Timer? = null
    private var runStartTimeMillis: Long? = null

    private val toggleResultAction = object : ToggleAction(
        "Show Result MD",
        "Toggle Claude output and result MD",
        AllIcons.FileTypes.Text
    ) {
        override fun isSelected(e: AnActionEvent): Boolean = showingResult

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setShowingResult(state)
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.description = if (showingResult) {
                "Show Claude output"
            } else {
                "Show Result MD"
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private val resultListener: (VirtualFile?) -> Unit = { file ->
        ApplicationManager.getApplication().invokeLater {
            updateResultView(file)
            if (file != null) setShowingResult(true)
        }
    }

    private val showConsoleListener: () -> Unit = {
        ApplicationManager.getApplication().invokeLater {
            setShowingResult(false)
        }
    }

    private val runStateListener = object : ClaudeConsoleService.RunStateListener {
        override fun onRunStarted(startTimeMillis: Long) {
            ApplicationManager.getApplication().invokeLater {
                runStartTimeMillis = startTimeMillis
                applyElapsedTimeVisibility()
                if (elapsedTimeLabel.isVisible) {
                    updateElapsedLabel(0L, running = true)
                    startElapsedTimer()
                }
            }
        }

        override fun onRunFinished(elapsedMillis: Long) {
            ApplicationManager.getApplication().invokeLater {
                stopElapsedTimer()
                runStartTimeMillis = null
                if (elapsedTimeLabel.isVisible) {
                    updateElapsedLabel(elapsedMillis, running = false)
                }
            }
        }
    }

    init {
        service.console = console

        resultContainer.add(resultPlaceholder, BorderLayout.CENTER)
        cardPanel.add(console.component, CARD_CONSOLE)
        cardPanel.add(resultContainer, CARD_RESULT)
        cardLayout.show(cardPanel, CARD_CONSOLE)

        val stopAction = object : AnAction(
            "Stop",
            "Stop the running Claude CLI process",
            AllIcons.Actions.Suspend
        ) {
            override fun actionPerformed(e: AnActionEvent) = service.stopProcess()
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = service.isRunning
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val actionGroup = DefaultActionGroup(stopAction)
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("PromptWorkToolbar", actionGroup, false)
        toolbar.targetComponent = this

        val headerBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        val headerToolbar = ActionManager.getInstance()
            .createActionToolbar("PromptWorkHeaderToolbar", DefaultActionGroup(toggleResultAction), true)
        headerToolbar.targetComponent = this
        headerBar.add(headerToolbar.component)

        add(headerBar, BorderLayout.NORTH)
        add(toolbar.component, BorderLayout.WEST)
        add(cardPanel, BorderLayout.CENTER)
        add(elapsedTimeLabel, BorderLayout.SOUTH)

        toolWindow.setTitleActions(emptyList())
        try {
            toolWindow.title = ""
        } catch (_: Exception) {
            // Older platforms may not allow clearing the title; safe to ignore.
        }

        service.addResultFileListener(resultListener)
        service.addShowConsoleListener(showConsoleListener)
        service.addRunStateListener(runStateListener)
        updateResultView(service.latestResultFile)
        applyElapsedTimeVisibility()
        service.lastFinishedElapsedMillis?.let {
            if (elapsedTimeLabel.isVisible) updateElapsedLabel(it, running = false)
        }
    }

    private fun setShowingResult(state: Boolean) {
        if (showingResult == state) return
        showingResult = state
        cardLayout.show(cardPanel, if (state) CARD_RESULT else CARD_CONSOLE)
    }

    private fun updateResultView(file: VirtualFile?) {
        currentResultFileEditor?.let { Disposer.dispose(it) }
        currentResultFileEditor = null
        resultContainer.removeAll()

        val target = if (file != null && file.isValid) file else null
        if (target == null) {
            resultContainer.add(resultPlaceholder, BorderLayout.CENTER)
        } else {
            val result = createPreviewEditor(target)
            if (result.editor == null) {
                resultContainer.add(
                    JBLabel("Could not load preview: ${target.name} — ${result.diagnostic}", JBLabel.CENTER),
                    BorderLayout.CENTER
                )
            } else {
                currentResultFileEditor = result.editor
                resultContainer.add(result.editor.component, BorderLayout.CENTER)
            }
        }
        resultContainer.revalidate()
        resultContainer.repaint()
    }

    private data class PreviewResult(val editor: FileEditor?, val diagnostic: String)

    private fun createPreviewEditor(file: VirtualFile): PreviewResult {
        val log = thisLogger()
        val providers = try {
            FileEditorProviderManager.getInstance().getProviderList(project, file)
        } catch (e: Exception) {
            log.error("Failed to load provider list for ${file.path}", e)
            return PreviewResult(null, "provider list error: ${e.message ?: e.javaClass.simpleName}")
        }

        val providerIds = providers.map { it.editorTypeId }
        log.info("FileEditorProviders for ${file.name} (type=${file.fileType.name}): $providerIds")

        val preferred = providers.firstOrNull { it.editorTypeId == MARKDOWN_PREVIEW_EDITOR_TYPE_ID }
            ?: providers.firstOrNull { it.editorTypeId == MARKDOWN_SPLIT_EDITOR_TYPE_ID }
            ?: providers.firstOrNull { it.editorTypeId.contains("markdown", ignoreCase = true) }
            ?: return PreviewResult(null, "no markdown provider (available: ${providerIds.joinToString()})")

        val editor = try {
            preferred.createEditor(project, file)
        } catch (e: Exception) {
            log.error("createEditor failed for ${preferred.editorTypeId} on ${file.path}", e)
            return PreviewResult(null, "createEditor failed (${preferred.editorTypeId}): ${e.message ?: e.javaClass.simpleName}")
        }

        if (editor is TextEditorWithPreview) {
            try {
                editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
            } catch (e: Exception) {
                log.warn("Failed to switch to SHOW_PREVIEW layout", e)
            }
        }
        return PreviewResult(editor, "ok (${preferred.editorTypeId})")
    }

    private fun applyElapsedTimeVisibility() {
        val visible = SimpleSettings.getInstance().state.showElapsedTime
        elapsedTimeLabel.isVisible = visible
        if (!visible) {
            stopElapsedTimer()
            elapsedTimeLabel.text = ""
        }
    }

    private fun startElapsedTimer() {
        stopElapsedTimer()
        val timer = Timer(ELAPSED_TICK_MILLIS) {
            val start = runStartTimeMillis ?: return@Timer
            updateElapsedLabel(System.currentTimeMillis() - start, running = true)
        }
        timer.isRepeats = true
        timer.start()
        elapsedTimer = timer
    }

    private fun stopElapsedTimer() {
        elapsedTimer?.stop()
        elapsedTimer = null
    }

    private fun updateElapsedLabel(elapsedMillis: Long, running: Boolean) {
        val prefix = if (running) "Elapsed: " else "Elapsed (finished): "
        elapsedTimeLabel.text = prefix + formatElapsed(elapsedMillis)
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val safeMillis = if (elapsedMillis < 0) 0 else elapsedMillis
        val totalSeconds = safeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (safeMillis % 1000) / 100
        return if (minutes > 0) {
            "%dm %02d.%ds".format(minutes, seconds, tenths)
        } else {
            "%d.%ds".format(seconds, tenths)
        }
    }

    fun dispose() {
        service.removeResultFileListener(resultListener)
        service.removeShowConsoleListener(showConsoleListener)
        service.removeRunStateListener(runStateListener)
        stopElapsedTimer()
        currentResultFileEditor?.let { Disposer.dispose(it) }
        currentResultFileEditor = null
    }

    companion object {
        private const val ELAPSED_TICK_MILLIS = 100
    }
}
