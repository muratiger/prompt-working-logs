package com.github.muratiger.promptworkinglogs.runner.formatter

import com.github.muratiger.promptworkinglogs.domain.OutputEvent
import com.github.muratiger.promptworkinglogs.domain.OutputEventFormatter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Claude CLI `--output-format stream-json` の 1 行を [OutputEvent] に解析し、
 * 人間可読な日本語テキストへ整形する [OutputEventFormatter] 実装。
 *
 * 既存テスト互換性のため、純関数のクラスとして提供する（`object` から
 * `class` へ変更し、コンストラクタ引数なしで `JsonOutputFormatter()` でも、
 * `companion object` の `format(line)` でもアクセスできる）。
 */
class JsonOutputFormatter : OutputEventFormatter {

    override fun format(line: String): String? = Companion.format(line)

    companion object {
        private const val EDIT_CONTENT_PREVIEW_MIN_LENGTH = 10
        private const val MILLIS_TO_SECONDS = 1000.0
        private const val DURATION_FORMAT = "%.1f秒"
        private const val COST_FORMAT = "$%.4f"

        private val TOOL_NAME_JA: Map<String, String> = mapOf(
            "Read" to "ファイル読み込み",
            "Edit" to "ファイル編集",
            "Write" to "ファイル作成",
            "Bash" to "コマンド実行",
            "Glob" to "ファイル検索",
            "Grep" to "テキスト検索",
            "WebSearch" to "Web検索",
            "WebFetch" to "Webページ取得",
            "Task" to "タスク実行",
        )

        fun format(line: String): String? {
            val event = parse(line) ?: return null
            return render(event)
        }

        /**
         * stream-json 1 行を [OutputEvent] に変換する。表示しない行は null を返す。
         */
        fun parse(line: String): OutputEvent? {
            if (line.isBlank()) return null
            return try {
                val json = JsonParser.parseString(line).asJsonObject
                when (json.get("type")?.asString) {
                    "system" -> parseSystem(json)
                    "assistant" -> parseAssistant(json)
                    "user" -> parseUser(json)
                    "result" -> parseResult(json)
                    else -> null
                }
            } catch (_: JsonSyntaxException) {
                OutputEvent.RawText(line)
            } catch (_: IllegalStateException) {
                null
            }
        }

        fun render(event: OutputEvent): String = when (event) {
            is OutputEvent.SystemInit -> "🚀 セッション開始 (モデル: ${event.model})"
            is OutputEvent.AssistantText -> "💬 応答:\n${event.text}"
            is OutputEvent.AssistantThinking -> "🧠 思考中:\n${event.text}"
            is OutputEvent.ToolUse -> renderToolUse(event)
            OutputEvent.ToolResultSuccess -> "✅ ツール実行完了"
            OutputEvent.ToolResultError -> "❌ ツール実行エラー"
            is OutputEvent.ResultSuccess -> {
                val durationSec = event.durationMs / MILLIS_TO_SECONDS
                "✨ 完了 (所要時間: ${DURATION_FORMAT.format(durationSec)}, " +
                    "ターン数: ${event.numTurns}, コスト: ${COST_FORMAT.format(event.totalCostUsd)})"
            }
            OutputEvent.ResultError -> "❌ エラー発生"
            is OutputEvent.ResultOther -> "📋 結果: ${event.subtype}"
            is OutputEvent.RawText -> event.text
        }

        private fun renderToolUse(event: OutputEvent.ToolUse): String {
            val nameLabel = TOOL_NAME_JA[event.toolName] ?: event.toolName
            val preview = event.editPreview
            if (preview != null) {
                return "🔧 $nameLabel: ${event.description}\n📝 追記内容:\n$preview"
            }
            return "🔧 $nameLabel" + if (event.description.isNotEmpty()) " (${event.description})" else ""
        }

        private fun parseSystem(json: JsonObject): OutputEvent? {
            val subtype = json.get("subtype")?.asString ?: return null
            return when (subtype) {
                "init" -> OutputEvent.SystemInit(json.get("model")?.asString ?: "不明")
                else -> null
            }
        }

        private fun parseAssistant(json: JsonObject): OutputEvent? {
            val message = json.getAsJsonObject("message") ?: return null
            val content = message.getAsJsonArray("content") ?: return null
            if (content.size() == 0) return null

            val results = mutableListOf<String>()
            for (i in 0 until content.size()) {
                val item = content[i].asJsonObject
                when (item.get("type")?.asString) {
                    "thinking" -> item.get("thinking")?.asString?.let {
                        results.add(render(OutputEvent.AssistantThinking(it)))
                    }
                    "text" -> item.get("text")?.asString?.let {
                        results.add(render(OutputEvent.AssistantText(it)))
                    }
                    "tool_use" -> parseToolUse(item)?.let {
                        results.add(render(it))
                    }
                }
            }
            if (results.isEmpty()) return null
            return OutputEvent.RawText(results.joinToString("\n"))
        }

        private fun parseToolUse(item: JsonObject): OutputEvent.ToolUse? {
            val toolName = item.get("name")?.asString ?: "不明"
            val input = item.getAsJsonObject("input")
            val description = input?.get("description")?.asString
                ?: input?.get("command")?.asString
                ?: input?.get("file_path")?.asString
                ?: ""

            val editPreview = if (toolName == "Edit") {
                val newString = input?.get("new_string")?.asString
                if (newString != null && newString.length > EDIT_CONTENT_PREVIEW_MIN_LENGTH) newString else null
            } else null

            return OutputEvent.ToolUse(toolName, description, editPreview)
        }

        private fun parseUser(json: JsonObject): OutputEvent? {
            val message = json.getAsJsonObject("message") ?: return null
            val content = message.getAsJsonArray("content") ?: return null
            if (content.size() == 0) return null
            val first = content[0].asJsonObject
            if (first.get("type")?.asString != "tool_result") return null
            val isError = first.get("is_error")?.asBoolean ?: false
            return if (isError) OutputEvent.ToolResultError else OutputEvent.ToolResultSuccess
        }

        private fun parseResult(json: JsonObject): OutputEvent {
            val subtype = json.get("subtype")?.asString
            val durationMs = json.get("duration_ms")?.asLong ?: 0L
            val numTurns = json.get("num_turns")?.asInt ?: 0
            val cost = json.get("total_cost_usd")?.asDouble ?: 0.0
            return when (subtype) {
                "success" -> OutputEvent.ResultSuccess(durationMs, numTurns, cost)
                "error" -> OutputEvent.ResultError
                null -> OutputEvent.ResultOther("null")
                else -> OutputEvent.ResultOther(subtype)
            }
        }
    }
}
