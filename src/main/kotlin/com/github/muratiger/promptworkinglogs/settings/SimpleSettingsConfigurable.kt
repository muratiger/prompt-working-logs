package com.github.muratiger.promptworkinglogs.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class SimpleSettingsConfigurable : Configurable {

    private lateinit var watchedDirectoryField: JBTextField
    private lateinit var cliCommandArea: JBTextArea
    private lateinit var outputLanguageComboBox: ComboBox<String>

    override fun getDisplayName(): String = "Prompt Work"

    override fun createComponent(): JComponent {
        val watchedField = JBTextField()
        val commandArea = JBTextArea(CLI_COMMAND_ROWS, CLI_COMMAND_COLUMNS).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val languageComboBox = ComboBox(
            DefaultComboBoxModel(SimpleSettings.SUPPORTED_OUTPUT_LANGUAGES.toTypedArray())
        )
        watchedDirectoryField = watchedField
        cliCommandArea = commandArea
        outputLanguageComboBox = languageComboBox

        val commandPanel = JPanel(BorderLayout()).apply {
            add(JScrollPane(commandArea), BorderLayout.CENTER)
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Watched Directory:"), watchedField, 1, false)
            .addComponent(JBLabel("監視対象ディレクトリ（プロジェクトルートからの相対パス）"))
            .addSeparator()
            .addLabeledComponent(JBLabel("Output Language:"), languageComboBox, 1, false)
            .addComponent(JBLabel("実行ログの出力言語（\${language} に置換されます）"))
            .addSeparator()
            .addLabeledComponent(JBLabel("CLI Command:"), commandPanel, 1, true)
            .addComponent(JBLabel("\${filePath} は対象ファイルの相対パスに置換されます"))
            .addComponent(JBLabel("\${dirPath} は対象ファイルと同名のディレクトリパス（拡張子除去・末尾 '/'）に置換されます"))
            .addComponent(JBLabel("\${language} は Output Language の選択値に置換されます"))
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        if (!::watchedDirectoryField.isInitialized) return false
        val settings = SimpleSettings.getInstance()
        return watchedDirectoryField.text != settings.state.watchedDirectory ||
                cliCommandArea.text != settings.state.cliCommand ||
                outputLanguageComboBox.selectedItem != settings.state.outputLanguage
    }

    override fun apply() {
        if (!::watchedDirectoryField.isInitialized) return
        val settings = SimpleSettings.getInstance()
        settings.state.watchedDirectory = watchedDirectoryField.text
        settings.state.cliCommand = cliCommandArea.text
        settings.state.outputLanguage =
            outputLanguageComboBox.selectedItem as? String ?: SimpleSettings.DEFAULT_OUTPUT_LANGUAGE
    }

    override fun reset() {
        if (!::watchedDirectoryField.isInitialized) return
        val settings = SimpleSettings.getInstance()
        watchedDirectoryField.text = settings.state.watchedDirectory
        cliCommandArea.text = settings.state.cliCommand
        outputLanguageComboBox.selectedItem = settings.state.outputLanguage
    }

    override fun disposeUIResources() {
        // lateinit fields cannot be reset to null; component lifecycle is owned by the IDE.
    }

    companion object {
        private const val CLI_COMMAND_ROWS = 5
        private const val CLI_COMMAND_COLUMNS = 50
    }
}
