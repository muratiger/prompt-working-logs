package com.github.muratiger.promptworkinglogs.domain

/**
 * Abstraction that formats a single line of Claude CLI output into a string
 * for UI display. When the return value is null, the line is not shown in the UI.
 */
fun interface OutputEventFormatter {
    fun format(line: String): String?
}
