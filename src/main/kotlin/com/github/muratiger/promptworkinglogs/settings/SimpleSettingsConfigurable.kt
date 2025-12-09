package com.github.muratiger.promptworkinglogs.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class SimpleSettingsConfigurable : Configurable {

    private var watchedDirectoryField: JBTextField? = null
    private var cliCommandArea: JBTextArea? = null

    override fun getDisplayName(): String = "Prompt Work"

    override fun createComponent(): JComponent {
        watchedDirectoryField = JBTextField()
        cliCommandArea = JBTextArea(5, 50).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        val commandPanel = JPanel(BorderLayout()).apply {
            add(JScrollPane(cliCommandArea), BorderLayout.CENTER)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Watched Directory:"), watchedDirectoryField!!, 1, false)
            .addComponent(JBLabel("監視対象ディレクトリ（プロジェクトルートからの相対パス）"))
            .addSeparator()
            .addLabeledComponent(JBLabel("CLI Command:"), commandPanel, 1, true)
            .addComponent(JBLabel("\${filePath} は対象ファイルの相対パスに置換されます"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = SimpleSettings.getInstance()
        return watchedDirectoryField?.text != settings.state.watchedDirectory ||
                cliCommandArea?.text != settings.state.cliCommand
    }

    override fun apply() {
        val settings = SimpleSettings.getInstance()
        settings.state.watchedDirectory = watchedDirectoryField?.text ?: settings.state.watchedDirectory
        settings.state.cliCommand = cliCommandArea?.text ?: settings.state.cliCommand
    }

    override fun reset() {
        val settings = SimpleSettings.getInstance()
        watchedDirectoryField?.text = settings.state.watchedDirectory
        cliCommandArea?.text = settings.state.cliCommand
    }

    override fun disposeUIResources() {
        watchedDirectoryField = null
        cliCommandArea = null
    }
}
