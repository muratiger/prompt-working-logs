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
        var watchedDirectory: String = "prompt-work",
        var cliCommand: String = "\$HOME/.claude/local/claude -p \"Use the last section (excluding metadata sections) of \${filePath} as the prompt input. Append a 3-line summary of the output and the session ID at the end of that section. Please provide ALL output entirely in Japanese. This includes your internal thinking/reasoning process (the thinking sections must be written in Japanese, not English). Output the processing logs and detailed results to the \${dirPath} directory as a markdown file named with the section name and current datetime.\" --dangerously-skip-permissions --output-format stream-json --verbose"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): SimpleSettings =
            ApplicationManager.getApplication().getService(SimpleSettings::class.java)
    }
}
