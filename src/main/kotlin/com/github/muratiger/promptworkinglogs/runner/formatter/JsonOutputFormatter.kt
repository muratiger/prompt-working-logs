package com.github.muratiger.promptworkinglogs.runner.formatter

import com.github.muratiger.promptworkinglogs.domain.OutputEvent
import com.github.muratiger.promptworkinglogs.domain.OutputEventFormatter
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * [OutputEventFormatter] implementation that parses a single line of Claude CLI
 * `--output-format stream-json` into an [OutputEvent] and formats it into
 * human-readable English text.
 *
 * Provided as a pure-function class for compatibility with existing tests
 * (changed from `object` to `class` so it can be accessed both via
 * `JsonOutputFormatter()` with no constructor arguments and via the
 * `companion object`'s `format(line)`).
 */
class JsonOutputFormatter : OutputEventFormatter {

    override fun format(line: String): String? = Companion.format(line)

    companion object {
        private const val EDIT_CONTENT_PREVIEW_MIN_LENGTH = 10
        private const val MILLIS_TO_SECONDS = 1000.0
        private const val DURATION_FORMAT = "%.1fs"
        private const val COST_FORMAT = "$%.4f"

        private val TOOL_NAME_LABELS: Map<String, String> = mapOf(
            "Read" to "Read file",
            "Edit" to "Edit file",
            "Write" to "Create file",
            "Bash" to "Run command",
            "Glob" to "Find files",
            "Grep" to "Search text",
            "WebSearch" to "Web search",
            "WebFetch" to "Fetch web page",
            "Task" to "Run task",
        )

        fun format(line: String): String? {
            val event = parse(line) ?: return null
            return render(event)
        }

        /**
         * Converts a single stream-json line into an [OutputEvent]. Returns null
         * for lines that should not be displayed.
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
            is OutputEvent.SystemInit -> "🚀 Session started (model: ${event.model})"
            is OutputEvent.AssistantText -> "💬 Response:\n${event.text}"
            is OutputEvent.AssistantThinking -> "🧠 Thinking:\n${event.text}"
            is OutputEvent.ToolUse -> renderToolUse(event)
            OutputEvent.ToolResultSuccess -> "✅ Tool execution completed"
            OutputEvent.ToolResultError -> "❌ Tool execution failed"
            is OutputEvent.ResultSuccess -> {
                val durationSec = event.durationMs / MILLIS_TO_SECONDS
                "✨ Completed (duration: ${DURATION_FORMAT.format(durationSec)}, " +
                    "turns: ${event.numTurns}, cost: ${COST_FORMAT.format(event.totalCostUsd)})"
            }
            OutputEvent.ResultError -> "❌ Error occurred"
            is OutputEvent.ResultOther -> "📋 Result: ${event.subtype}"
            is OutputEvent.RawText -> event.text
        }

        private fun renderToolUse(event: OutputEvent.ToolUse): String {
            val nameLabel = TOOL_NAME_LABELS[event.toolName] ?: event.toolName
            val preview = event.editPreview
            if (preview != null) {
                return "🔧 $nameLabel: ${event.description}\n📝 New content:\n$preview"
            }
            return "🔧 $nameLabel" + if (event.description.isNotEmpty()) " (${event.description})" else ""
        }

        private fun parseSystem(json: JsonObject): OutputEvent? {
            val subtype = json.get("subtype")?.asString ?: return null
            return when (subtype) {
                "init" -> OutputEvent.SystemInit(json.get("model")?.asString ?: "unknown")
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
            val toolName = item.get("name")?.asString ?: "unknown"
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
