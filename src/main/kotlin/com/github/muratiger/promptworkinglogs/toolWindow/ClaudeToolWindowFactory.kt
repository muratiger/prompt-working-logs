package com.github.muratiger.promptworkinglogs.toolwindow

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class ClaudeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // コンソールビューの作成
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console

        // プロジェクトサービスにコンソールを保存
        ClaudeConsoleService.getInstance(project).console = console

        // 中止ボタンアクション
        val stopAction = object : AnAction("中止", "実行中のClaude CLIプロセスを中止します", AllIcons.Actions.Suspend) {
            override fun actionPerformed(e: AnActionEvent) {
                val service = ClaudeConsoleService.getInstance(project)
                service.stopProcess()
            }

            override fun update(e: AnActionEvent) {
                val service = ClaudeConsoleService.getInstance(project)
                e.presentation.isEnabled = service.isRunning
            }
        }

        // ツールバーの作成
        val actionGroup = DefaultActionGroup(stopAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("PromptWorkToolbar", actionGroup, false)

        // パネルにツールバーとコンソールを配置
        val panel = JPanel(BorderLayout())
        toolbar.targetComponent = panel
        panel.add(toolbar.component, BorderLayout.WEST)
        panel.add(console.component, BorderLayout.CENTER)

        // UI に追加
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
