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
        var cliCommand: String = "\$HOME/.claude/local/claude -p \"\${filePath} の一番下のセクション（メタデータセクションを除く）をプロンプトとして入力。セクションの末尾に出力結果を3行でまとめてものとセッションIDを追記してください。処理しているときのログと回答は日本語でお願いします。\" --dangerously-skip-permissions --output-format stream-json --verbose"
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
