package com.github.muratiger.promptworkinglogs.domain

/**
 * Claude CLI `--output-format stream-json` の 1 行を表すドメインモデル。
 * パース結果はこの sealed class のいずれかに分類され、UI 表示用フォーマッタは
 * これを純粋なテキストへ変換する。
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
