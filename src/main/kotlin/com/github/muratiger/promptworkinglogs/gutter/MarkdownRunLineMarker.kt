package com.github.muratiger.promptworkinglogs.gutter

import com.github.muratiger.promptworkinglogs.runner.ClaudeCliRunner
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent
import javax.swing.Icon

class MarkdownRunLineMarker : LineMarkerProviderDescriptor() {

    override fun getName(): String = "Claude CLI Runner"

    override fun getIcon(): Icon = ICON

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // ファイルの最初の要素にのみマーカーを表示
        if (element !is PsiFile) return null

        val file = element.containingFile?.virtualFile ?: return null

        // .mdファイルのみ対象
        if (file.extension != "md") return null

        val settings = SimpleSettings.getInstance()
        val watchedDir = settings.state.watchedDirectory

        // 監視対象ディレクトリ配下かチェック
        val project = element.project
        val basePath = project.basePath ?: return null
        val relativePath = file.path.removePrefix(basePath).removePrefix("/")

        if (!relativePath.startsWith(watchedDir)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            ICON,
            { "Run Claude CLI" },
            { e: MouseEvent, elt: PsiElement ->
                val psiFile = elt.containingFile?.virtualFile ?: return@LineMarkerInfo
                ClaudeCliRunner.run(elt.project, psiFile)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Run Claude CLI" }
        )
    }

    companion object {
        private val ICON: Icon = IconLoader.getIcon("/icons/claudeRun.svg", MarkdownRunLineMarker::class.java)
    }
}
