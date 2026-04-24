package com.github.muratiger.promptworkinglogs.toolwindow

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
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JPanel

class ClaudeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ClaudeConsolePanel(project, toolWindow)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        Disposer.register(content) { panel.dispose() }
        toolWindow.contentManager.addContent(content)
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
        headerBar.add(JBLabel("prompt-work"))
        val headerToolbar = ActionManager.getInstance()
            .createActionToolbar("PromptWorkHeaderToolbar", DefaultActionGroup(toggleResultAction), true)
        headerToolbar.targetComponent = this
        headerBar.add(headerToolbar.component)

        add(headerBar, BorderLayout.NORTH)
        add(toolbar.component, BorderLayout.WEST)
        add(cardPanel, BorderLayout.CENTER)

        toolWindow.setTitleActions(emptyList())
        try {
            toolWindow.title = ""
        } catch (_: Throwable) {
            // Older platforms may not allow clearing the title; safe to ignore.
        }

        service.addResultFileListener(resultListener)
        updateResultView(service.latestResultFile)
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
        } catch (e: Throwable) {
            log.warn("Failed to load provider list for ${file.path}", e)
            return PreviewResult(null, "provider list error: ${e.javaClass.simpleName}")
        }

        val providerIds = providers.map { it.editorTypeId }
        log.info("FileEditorProviders for ${file.name} (type=${file.fileType.name}): $providerIds")

        val preferred = providers.firstOrNull { it.editorTypeId == MARKDOWN_PREVIEW_EDITOR_TYPE_ID }
            ?: providers.firstOrNull { it.editorTypeId == MARKDOWN_SPLIT_EDITOR_TYPE_ID }
            ?: providers.firstOrNull { it.editorTypeId.contains("markdown", ignoreCase = true) }

        if (preferred == null) {
            return PreviewResult(null, "no markdown provider (available: ${providerIds.joinToString()})")
        }

        val editor = try {
            preferred.createEditor(project, file)
        } catch (e: Throwable) {
            log.warn("createEditor failed for ${preferred.editorTypeId} on ${file.path}", e)
            return PreviewResult(null, "createEditor failed (${preferred.editorTypeId}): ${e.javaClass.simpleName}")
        }

        if (editor is TextEditorWithPreview) {
            try {
                editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
            } catch (e: Throwable) {
                log.warn("Failed to switch to SHOW_PREVIEW layout", e)
            }
        }
        return PreviewResult(editor, "ok (${preferred.editorTypeId})")
    }

    fun dispose() {
        service.removeResultFileListener(resultListener)
        currentResultFileEditor?.let { Disposer.dispose(it) }
        currentResultFileEditor = null
    }
}
