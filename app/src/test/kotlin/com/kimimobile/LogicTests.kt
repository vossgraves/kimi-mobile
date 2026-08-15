package com.kimimobile

import com.kimimobile.data.Calculator
import com.kimimobile.data.Models
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic that breaks silently if wrong: the model-suffix protocol the proxy
 * relies on, and the local expression evaluator.
 */
class LogicTests {

    @Test
    fun `resolve appends capability suffixes in proxy order`() {
        assertEquals("kimi-k2-0905-preview", Models.resolve("kimi-k2-0905-preview"))
        assertEquals(
            "kimi-k2-0905-preview-search",
            Models.resolve("kimi-k2-0905-preview", search = true),
        )
        assertEquals(
            "kimi-k2-0905-preview-math",
            Models.resolve("kimi-k2-0905-preview", math = true),
        )
        assertEquals(
            "kimi-k2-0905-preview-search-math",
            Models.resolve("kimi-k2-0905-preview", search = true, math = true),
        )
    }

    @Test
    fun `research supersedes plain search`() {
        // Both flags on must not produce "-search-research"; the API takes one.
        assertEquals(
            "kimi-k2-0905-preview-research",
            Models.resolve("kimi-k2-0905-preview", search = true, research = true),
        )
    }

    @Test
    fun `context window follows the selected model`() {
        assertEquals(262_144L, Models.contextTokensFor("kimi-k2-0905-preview"))
        assertEquals(8_192L, Models.contextTokensFor("moonshot-v1-8k"))
        // Suffixed ids must still resolve to the base model's window.
        assertEquals(262_144L, Models.contextTokensFor("kimi-k2-0905-preview-search"))
    }

    @Test
    fun `longest prefix wins so thinking-turbo is not read as thinking`() {
        assertEquals("kimi-k2-thinking-turbo", Models.byId("kimi-k2-thinking-turbo")?.id)
        assertEquals("kimi-k2-thinking-turbo", Models.byId("kimi-k2-thinking-turbo-search")?.id)
        assertEquals("kimi-k2-thinking", Models.byId("kimi-k2-thinking-search")?.id)
        assertEquals(
            "moonshot-v1-8k-vision-preview",
            Models.byId("moonshot-v1-8k-vision-preview")?.id,
        )
    }

    @Test
    fun `calculator respects precedence and parentheses`() {
        assertEquals(14.0, Calculator.eval("2+3*4"), 0.0001)
        assertEquals(20.0, Calculator.eval("(2+3)*4"), 0.0001)
        assertEquals(8.0, Calculator.eval("2^3"), 0.0001)
        assertEquals(-6.0, Calculator.eval("-2*3"), 0.0001)
        assertEquals(1.0, Calculator.eval("10%3"), 0.0001)
    }

    @Test
    fun `calculator rejects division by zero instead of returning infinity`() {
        val failed = runCatching { Calculator.eval("1/0") }.isFailure
        assertTrue("division by zero must throw, not return Infinity", failed)
    }

    @Test
    fun `token estimate grows with content`() {
        val small = com.kimimobile.ui.ChatViewModel.estimateTokens(listOf("hello"))
        val large = com.kimimobile.ui.ChatViewModel.estimateTokens(listOf("hello".repeat(1000)))
        assertTrue(large > small * 10)
    }
}
