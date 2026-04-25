package com.github.muratiger.promptworkinglogs.domain

/**
 * Claude CLI からの 1 行出力を UI 表示用の文字列に整形する抽象。
 * 戻り値が null の場合、その行は UI に表示しない。
 */
fun interface OutputEventFormatter {
    fun format(line: String): String?
}
