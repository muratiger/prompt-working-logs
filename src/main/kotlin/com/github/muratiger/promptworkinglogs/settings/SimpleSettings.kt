package com.github.muratiger.promptworkinglogs.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "ClaudeRunnerSettings",
    storages = [Storage("claudeRunner.xml")]
)
class SimpleSettings : PersistentStateComponent<SimpleSettings.State> {

    data class State(
        var watchedDirectory: String = DEFAULT_WATCHED_DIRECTORY,
        var cliCommand: String = DEFAULT_CLI_COMMAND,
        var outputLanguage: String = DEFAULT_OUTPUT_LANGUAGE,
        // Bump CURRENT_DEFAULTS_VERSION whenever DEFAULT_CLI_COMMAND changes so existing
        // users' persisted state gets overwritten on the next plugin load.
        var defaultsVersion: Int = 0
    )

    private var myState = State(defaultsVersion = CURRENT_DEFAULTS_VERSION)

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        if (state.defaultsVersion < CURRENT_DEFAULTS_VERSION) {
            myState.cliCommand = DEFAULT_CLI_COMMAND
            myState.defaultsVersion = CURRENT_DEFAULTS_VERSION
        }
    }

    companion object {
        const val CURRENT_DEFAULTS_VERSION = 2

        const val DEFAULT_WATCHED_DIRECTORY = "prompt-work"
        const val DEFAULT_CLI_COMMAND =
            "\$HOME/.claude/local/claude -p \"Use the last section (excluding metadata sections) of \${filePath} as the prompt input. Append a 3-line summary of the output and the session ID at the end of that section. Please provide ALL output entirely in \${language}. This includes your internal thinking/reasoning process (the thinking sections must be written in \${language}). Output the processing logs and detailed results to the \${dirPath} directory as a markdown file named with the section name and current datetime.\" --dangerously-skip-permissions --output-format stream-json --verbose"

        const val LANGUAGE_ENGLISH = "English"
        const val LANGUAGE_JAPANESE = "Japanese"
        const val DEFAULT_OUTPUT_LANGUAGE = LANGUAGE_ENGLISH
        val SUPPORTED_OUTPUT_LANGUAGES = listOf(LANGUAGE_ENGLISH, LANGUAGE_JAPANESE)

        fun getInstance(): SimpleSettings =
            ApplicationManager.getApplication().getService(SimpleSettings::class.java)
    }
}
