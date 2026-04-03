package com.github.muratiger.promptworkinglogs.runner

import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.github.muratiger.promptworkinglogs.toolwindow.ClaudeConsoleService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

object ClaudeCliRunner {

    fun run(project: Project, file: VirtualFile) {
        // 全ドキュメントを保存
        FileDocumentManager.getInstance().saveAllDocuments()

        val settings = SimpleSettings.getInstance()
        val basePath = project.basePath ?: return

        // ファイルパスを相対パスに変換
        val relativePath = file.path.removePrefix(basePath).removePrefix("/")

        // ファイル名と同じ名前のディレクトリパスを生成（拡張子を除去）
        // 例: prompts/test1.md → prompts/test1/
        val dirPath = relativePath.substringBeforeLast(".") + "/"

        // CLIコマンドを構築（${filePath}, ${dirPath}を置換）
        val command = settings.state.cliCommand
            .replace("\${filePath}", relativePath)
            .replace("\${dirPath}", dirPath)

        // ToolWindow を取得して表示
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("prompt-work")
        toolWindow?.show()

        // プロジェクトサービスからコンソールを取得
        val console = ClaudeConsoleService.getInstance(project).console
        console?.clear()

        // PTYを使用してTTYをエミュレート
        val commandLine = PtyCommandLine(listOf("bash", "-lc", command))
            .withWorkDirectory(basePath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        try {
            val handler = KillableColoredProcessHandler(commandLine)

            // プロセスハンドラをサービスに保存（中止機能用）
            val service = ClaudeConsoleService.getInstance(project)
            service.processHandler = handler

            // JSON出力をパースして読みやすい形式で表示
            handler.addProcessListener(object : ProcessAdapter() {
                private val buffer = StringBuilder()

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    val text = event.text
                    buffer.append(text)

                    // 改行で区切ってJSONをパース
                    val lines = buffer.toString().split("\n")
                    buffer.clear()

                    for (i in lines.indices) {
                        val line = lines[i]
                        if (i == lines.lastIndex && !text.endsWith("\n")) {
                            // 最後の不完全な行はバッファに戻す
                            buffer.append(line)
                            continue
                        }
                        if (line.isBlank()) continue

                        val formattedText = formatJsonLine(line)
                        if (formattedText != null) {
                            val contentType = if (outputType === ProcessOutputTypes.STDERR) {
                                ConsoleViewContentType.ERROR_OUTPUT
                            } else {
                                ConsoleViewContentType.NORMAL_OUTPUT
                            }
                            console?.print(formattedText + "\n", contentType)
                        }
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    // プロセスハンドラ参照をクリア
                    service.processHandler = null

                    // ファイルの親ディレクトリを再帰的にリフレッシュ（新規作成ファイル・ディレクトリもProject viewに反映）
                    file.parent?.refresh(true, true)

                    if (event.exitCode != 0) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("prompt-work")
                            .createNotification(
                                "Claude CLI 失敗",
                                "終了コード: ${event.exitCode}。詳細は 'Claude Runner' ツールウィンドウを確認してください。",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    } else {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("prompt-work")
                            .createNotification(
                                "Claude CLI 完了",
                                "ファイル更新: $relativePath",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                }
            })
            handler.startNotify()
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("prompt-work")
                .createNotification(
                    "Claude CLI エラー",
                    e.message ?: "不明なエラー",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }

    private fun formatJsonLine(line: String): String? {
        try {
            val json = JsonParser.parseString(line).asJsonObject
            val type = json.get("type")?.asString ?: return null

            return when (type) {
                "system" -> {
                    val subtype = json.get("subtype")?.asString
                    when (subtype) {
                        "init" -> {
                            val model = json.get("model")?.asString ?: "不明"
                            "🚀 セッション開始 (モデル: $model)"
                        }
                        else -> null
                    }
                }
                "assistant" -> {
                    val message = json.getAsJsonObject("message")
                    val content = message?.getAsJsonArray("content")
                    if (content != null && content.size() > 0) {
                        val results = mutableListOf<String>()

                        for (i in 0 until content.size()) {
                            val contentItem = content[i].asJsonObject
                            val contentType = contentItem.get("type")?.asString

                            when (contentType) {
                                "thinking" -> {
                                    val thinking = contentItem.get("thinking")?.asString
                                    if (thinking != null) {
                                        results.add("🧠 思考中:\n$thinking")
                                    }
                                }
                                "text" -> {
                                    val text = contentItem.get("text")?.asString
                                    if (text != null) {
                                        results.add("💬 応答:\n$text")
                                    }
                                }
                                "tool_use" -> {
                                    val toolName = contentItem.get("name")?.asString ?: "不明"
                                    val input = contentItem.getAsJsonObject("input")

                                    // ツール名を日本語に変換
                                    val toolNameJa = when (toolName) {
                                        "Read" -> "ファイル読み込み"
                                        "Edit" -> "ファイル編集"
                                        "Write" -> "ファイル作成"
                                        "Bash" -> "コマンド実行"
                                        "Glob" -> "ファイル検索"
                                        "Grep" -> "テキスト検索"
                                        "WebSearch" -> "Web検索"
                                        "WebFetch" -> "Webページ取得"
                                        "Task" -> "タスク実行"
                                        else -> toolName
                                    }

                                    val description = input?.get("description")?.asString
                                        ?: input?.get("command")?.asString
                                        ?: input?.get("file_path")?.asString
                                        ?: ""

                                    // Editツールの場合、編集内容も表示
                                    if (toolName == "Edit") {
                                        val newString = input?.get("new_string")?.asString
                                        if (newString != null && newString.length > 10) {
                                            results.add("🔧 $toolNameJa: $description\n📝 追記内容:\n$newString")
                                        } else {
                                            results.add("🔧 $toolNameJa" + if (description.isNotEmpty()) " ($description)" else "")
                                        }
                                    } else {
                                        results.add("🔧 $toolNameJa" + if (description.isNotEmpty()) " ($description)" else "")
                                    }
                                }
                            }
                        }

                        if (results.isNotEmpty()) results.joinToString("\n") else null
                    } else null
                }
                "user" -> {
                    val message = json.getAsJsonObject("message")
                    val content = message?.getAsJsonArray("content")
                    if (content != null && content.size() > 0) {
                        val firstContent = content[0].asJsonObject
                        val contentType = firstContent.get("type")?.asString

                        if (contentType == "tool_result") {
                            val isError = firstContent.get("is_error")?.asBoolean ?: false
                            if (isError) {
                                "❌ ツール実行エラー"
                            } else {
                                "✅ ツール実行完了"
                            }
                        } else null
                    } else null
                }
                "result" -> {
                    val subtype = json.get("subtype")?.asString
                    val durationMs = json.get("duration_ms")?.asLong ?: 0
                    val durationSec = durationMs / 1000.0
                    val numTurns = json.get("num_turns")?.asInt ?: 0
                    val cost = json.get("total_cost_usd")?.asDouble ?: 0.0

                    when (subtype) {
                        "success" -> "✨ 完了 (所要時間: %.1f秒, ターン数: %d, コスト: $%.4f)".format(durationSec, numTurns, cost)
                        "error" -> "❌ エラー発生"
                        else -> "📋 結果: $subtype"
                    }
                }
                else -> null
            }
        } catch (e: JsonSyntaxException) {
            // JSONでない場合はそのまま表示
            return if (line.isNotBlank()) line else null
        } catch (e: Exception) {
            return null
        }
    }
}
