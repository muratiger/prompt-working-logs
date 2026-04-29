package com.github.muratiger.promptworkinglogs.runner.formatter

import com.github.muratiger.promptworkinglogs.domain.OutputEvent
import com.github.muratiger.promptworkinglogs.domain.OutputEventFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests the separation between the parse stage that decomposes a single
 * stream-json line into an [OutputEvent] and the render stage that produces
 * a string from the parse result.
 */
class JsonOutputFormatterParseTest {

    @Test
    fun `parse system init produces SystemInit with model`() {
        val event = JsonOutputFormatter.parse("""{"type":"system","subtype":"init","model":"opus"}""")
        assertEquals(OutputEvent.SystemInit("opus"), event)
    }

    @Test
    fun `parse system without subtype is null`() {
        assertNull(JsonOutputFormatter.parse("""{"type":"system"}"""))
    }

    @Test
    fun `parse user tool_result error produces ToolResultError`() {
        val event = JsonOutputFormatter.parse(
            """{"type":"user","message":{"content":[{"type":"tool_result","is_error":true}]}}"""
        )
        assertSame(OutputEvent.ToolResultError, event)
    }

    @Test
    fun `parse result success keeps duration and cost`() {
        val event = JsonOutputFormatter.parse(
            """{"type":"result","subtype":"success","duration_ms":1234,"num_turns":2,"total_cost_usd":0.5}"""
        )
        assertEquals(OutputEvent.ResultSuccess(1234L, 2, 0.5), event)
    }

    @Test
    fun `parse blank yields null`() {
        assertNull(JsonOutputFormatter.parse(""))
        assertNull(JsonOutputFormatter.parse("   "))
    }

    @Test
    fun `parse non-json produces RawText`() {
        val event = JsonOutputFormatter.parse("plain text")
        assertEquals(OutputEvent.RawText("plain text"), event)
    }

    @Test
    fun `render SystemInit and ResultSuccess match format outputs`() {
        assertEquals(
            "🚀 Session started (model: opus)",
            JsonOutputFormatter.render(OutputEvent.SystemInit("opus"))
        )
        assertEquals(
            "✨ Completed (duration: 1.2s, turns: 2, cost: \$0.5000)",
            JsonOutputFormatter.render(OutputEvent.ResultSuccess(1234L, 2, 0.5))
        )
    }

    @Test
    fun `formatter via interface delegates to companion`() {
        val formatter: OutputEventFormatter = JsonOutputFormatter()
        assertEquals(
            "🚀 Session started (model: opus)",
            formatter.format("""{"type":"system","subtype":"init","model":"opus"}""")
        )
        assertNull(formatter.format(""))
    }
}
