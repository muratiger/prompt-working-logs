package com.github.muratiger.promptworkinglogs.action

import com.github.muratiger.promptworkinglogs.runner.ClaudeCliRunner
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

class RunClaudeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 現在開いているファイルを取得
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: return

        // 設定を取得
        val settings = SimpleSettings.getInstance()
        val watchedDir = settings.state.watchedDirectory

        // watchedDirectory内のMarkdownファイルかチェック
        val basePath = project.basePath ?: return
        val relativePath = file.path.removePrefix(basePath).removePrefix("/")

        if (!relativePath.startsWith(watchedDir) || !file.name.endsWith(".md")) {
            return
        }

        // Claude CLIを実行
        ClaudeCliRunner.run(project, file)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: project?.let { FileEditorManager.getInstance(it).selectedFiles.firstOrNull() }

        // プロジェクトとファイルが存在し、Markdownファイルの場合に有効化
        val isEnabled = project != null && file != null && file.name.endsWith(".md")
        e.presentation.isEnabledAndVisible = isEnabled
    }
}
