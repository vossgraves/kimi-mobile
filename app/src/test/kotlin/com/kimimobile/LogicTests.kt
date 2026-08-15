package com.kimimobile

import com.kimimobile.data.Calculator
import com.kimimobile.data.Marketplace
import com.kimimobile.data.Models
import com.kimimobile.data.Provider
import com.kimimobile.data.Subagents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `zen models keep their id, kimi models take suffixes`() {
        // Zen has no -search variants; sending one would 400.
        assertEquals(
            "nemotron-3.5-lightning-free",
            Models.resolve("nemotron-3.5-lightning-free", search = true, math = true),
        )
        assertEquals(
            "kimi-k2-0905-preview-search",
            Models.resolve("kimi-k2-0905-preview", search = true),
        )
        assertEquals(Provider.ZEN, Models.providerOf("hy3-free"))
        assertEquals(Provider.KIMI, Models.providerOf("kimi-k2-thinking"))
    }

    @Test
    fun `subagent mentions only parse at the start with a known handle`() {
        assertEquals("researcher", Subagents.parseMention("@researcher find X")?.first?.handle)
        assertEquals("find X", Subagents.parseMention("@researcher find X")?.second)
        assertNull(Subagents.parseMention("email me @researcher later"))
        assertNull(Subagents.parseMention("@nobody do things"))
        assertNull(Subagents.parseMention("@researcher"))
        assertNull(Subagents.parseMention("plain message"))
    }

    @Test
    fun `install intent understands plain requests`() {
        val add = Marketplace.parseInstallIntent("add web search skill")
        assertEquals("web_search", add?.item?.id)
        assertEquals(true, add?.enable)

        val off = Marketplace.parseInstallIntent("turn off memory notes")
        assertEquals("memory", off?.item?.id)
        assertEquals(false, off?.enable)

        assertNull(Marketplace.parseInstallIntent("what is the weather today"))
        assertNull(Marketplace.parseInstallIntent("enable something unknown"))
    }

    @Test
    fun `token estimate grows with content`() {
        val small = com.kimimobile.ui.ChatViewModel.estimateTokens(listOf("hello"))
        val large = com.kimimobile.ui.ChatViewModel.estimateTokens(listOf("hello".repeat(1000)))
        assertTrue(large > small * 10)
    }
}
