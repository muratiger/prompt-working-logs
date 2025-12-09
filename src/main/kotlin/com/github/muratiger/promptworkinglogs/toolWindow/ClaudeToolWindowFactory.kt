package com.github.muratiger.promptworkinglogs.toolwindow

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class ClaudeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // コンソールビューの作成
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console

        // プロジェクトサービスにコンソールを保存
        ClaudeConsoleService.getInstance(project).console = console

        // UI に追加
        val content = toolWindow.contentManager.factory.createContent(console.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
