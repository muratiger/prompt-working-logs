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
        assertEquals("🚀 Session started (model: claude-opus)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `system init defaults model when missing`() {
        val line = """{"type":"system","subtype":"init"}"""
        assertEquals("🚀 Session started (model: unknown)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `system with unknown subtype is suppressed`() {
        assertNull(JsonOutputFormatter.format("""{"type":"system","subtype":"info"}"""))
    }

    @Test
    fun `assistant text content is rendered as response`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"text","text":"hello"}]}}"""
        assertEquals("💬 Response:\nhello", JsonOutputFormatter.format(line))
    }

    @Test
    fun `assistant thinking content is rendered`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"thinking","thinking":"thinking..."}]}}"""
        assertEquals("🧠 Thinking:\nthinking...", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_use translates known tool names to english labels`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file_path":"a.md"}}]}}"""
        assertEquals("🔧 Read file (a.md)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_use without description omits parens`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{}}]}}"""
        assertEquals("🔧 Run command", JsonOutputFormatter.format(line))
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
        assertTrue(out, out!!.contains("📝 New content:"))
        assertTrue(out, out.contains(newString))
    }

    @Test
    fun `Edit with short new_string falls back to summary line`() {
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Edit","input":{"file_path":"x.md","new_string":"short"}}]}}"""
        assertEquals("🔧 Edit file (x.md)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_result success rendered`() {
        val line = """{"type":"user","message":{"content":[{"type":"tool_result","is_error":false}]}}"""
        assertEquals("✅ Tool execution completed", JsonOutputFormatter.format(line))
    }

    @Test
    fun `tool_result error rendered`() {
        val line = """{"type":"user","message":{"content":[{"type":"tool_result","is_error":true}]}}"""
        assertEquals("❌ Tool execution failed", JsonOutputFormatter.format(line))
    }

    @Test
    fun `result success line uses english number formatting`() {
        val line = """{"type":"result","subtype":"success","duration_ms":2500,"num_turns":3,"total_cost_usd":0.1234}"""
        assertEquals("✨ Completed (duration: 2.5s, turns: 3, cost: \$0.1234)", JsonOutputFormatter.format(line))
    }

    @Test
    fun `result error subtype rendered`() {
        val line = """{"type":"result","subtype":"error","duration_ms":0}"""
        assertEquals("❌ Error occurred", JsonOutputFormatter.format(line))
    }

    @Test
    fun `unknown event type is suppressed`() {
        assertNull(JsonOutputFormatter.format("""{"type":"telemetry"}"""))
    }
}
