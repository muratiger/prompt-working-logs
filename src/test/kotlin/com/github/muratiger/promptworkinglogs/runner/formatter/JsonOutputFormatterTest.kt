package com.github.muratiger.promptworkinglogs.runner.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonOutputFormatterTest {

    @Test
    fun `blank line returns null`() {
        assertNull(JsonOutputFormatter.format(""))
        assertNull(JsonOutputFormatter.format("   "))
    }

    @Test
    fun `non-json line is echoed back unchanged`() {
        assertEquals("plain text", JsonOutputFormatter.format("plain text"))
    }

    @Test
    fun `system init produces session start with model`() {
        val line = """{"type":"system","subtype":"init","model":"claude-opus"}"""
        assertEquals("🚀 セッション開始 (モデル: claude-opus)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `system init defaults model when missing`() {
        val line = """{"type":"system","subtype":"init"}"""
        assertEquals("🚀 セッション開始 (モデル: 不明)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `system with unknown subtype is suppressed`() {
        assertNull(JsonOutputFormatter.format("""{"type":"system","subtype":"info"}"""))
    }

    @Test
    fun `assistant text content is rendered as response`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"text","text":"hello"}]}}"""
        assertEquals("💬 応答:\nhello", JsonOutputFormatter.format(line))
    }

    @Test
    fun `assistant thinking content is rendered`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"thinking","thinking":"考え中"}]}}"""
        assertEquals("🧠 思考中:\n考え中", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_use translates known tool names to japanese`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file_path":"a.md"}}]}}"""
        assertEquals("🔧 ファイル読み込み (a.md)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_use without description omits parens`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{}}]}}"""
        assertEquals("🔧 コマンド実行", JsonOutputFormatter.format(line))
    }

    @Test
    fun `unknown tool keeps original name`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"CustomTool","input":{"description":"do x"}}]}}"""
        assertEquals("🔧 CustomTool (do x)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `Edit with long new_string includes preview`() {
        val newString = "1234567890123" // length > 10
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Edit","input":{"file_path":"x.md","new_string":"$newString"}}]}}"""
        val out = JsonOutputFormatter.format(line)
        assertTrue(out, out!!.contains("📝 追記内容:"))
        assertTrue(out, out.contains(newString))
    }

    @Test
    fun `Edit with short new_string falls back to summary line`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Edit","input":{"file_path":"x.md","new_string":"short"}}]}}"""
        assertEquals("🔧 ファイル編集 (x.md)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_result success rendered`() {
        val line = """{"type":"user","message":{"content":[{"type":"tool_result","is_error":false}]}}"""
        assertEquals("✅ ツール実行完了", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_result error rendered`() {
        val line = """{"type":"user","message":{"content":[{"type":"tool_result","is_error":true}]}}"""
        assertEquals("❌ ツール実行エラー", JsonOutputFormatter.format(line))
    }

    @Test
    fun `result success line uses japanese number formatting`() {
        val line = """{"type":"result","subtype":"success","duration_ms":2500,"num_turns":3,"total_cost_usd":0.1234}"""
        assertEquals("✨ 完了 (所要時間: 2.5秒, ターン数: 3, コスト: \$0.1234)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `result error subtype rendered`() {
        val line = """{"type":"result","subtype":"error","duration_ms":0}"""
        assertEquals("❌ エラー発生", JsonOutputFormatter.format(line))
    }

    @Test
    fun `unknown event type is suppressed`() {
        assertNull(JsonOutputFormatter.format("""{"type":"telemetry"}"""))
    }
}
