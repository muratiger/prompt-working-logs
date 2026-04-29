package com.github.muratiger.promptworkinglogs.domain

/**
 * Domain model representing a single line of Claude CLI `--output-format stream-json`
 * output. Parse results are classified into one of this sealed class's variants,
 * and the UI display formatter converts it into plain text.
 */
sealed class OutputEvent {
    data class SystemInit(val model: String) : OutputEvent()
    data class AssistantText(val text: String) : OutputEvent()
    data class AssistantThinking(val text: String) : OutputEvent()
    data class ToolUse(
        val toolName: String,
        val description: String,
        val editPreview: String?,
    ) : OutputEvent()
    data object ToolResultSuccess : OutputEvent()
    data object ToolResultError : OutputEvent()
    data class ResultSuccess(
        val durationMs: Long,
        val numTurns: Int,
        val totalCostUsd: Double,
    ) : OutputEvent()
    data object ResultError : OutputEvent()
    data class ResultOther(val subtype: String) : OutputEvent()
    data class RawText(val text: String) : OutputEvent()
}
