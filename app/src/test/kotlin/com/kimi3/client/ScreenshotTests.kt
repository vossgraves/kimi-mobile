package com.kimi3.client

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimi3.client.data.CatalogType
import com.kimi3.client.data.Marketplace
import com.kimi3.client.data.SkillEngine
import com.kimi3.client.ui.ChatMessage
import com.kimi3.client.ui.ContextState
import com.kimi3.client.ui.MessageRole
import com.kimi3.client.ui.screens.ChatScreenContent
import com.kimi3.client.ui.screens.MarketplaceScreenContent
import com.kimi3.client.ui.theme.KimiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the chat UI on the JVM (Robolectric) and writes PNGs to app/build/screenshots.
 * Verify the UI visually from the CI artifact.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val bitmap: Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        // Guard: a blank render (all one color) is a failure signal, not a screenshot.
        val sample = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        check(!(bitmap.width < 50 || bitmap.height < 50 || sample == Color.TRANSPARENT)) {
            "Screenshot $name came out blank — composable didn't render"
        }

        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun setChatContent(
        messages: List<ChatMessage>,
        isStreaming: Boolean = false,
        dark: Boolean = false,
        name: String,
        contextState: ContextState = ContextState(),
        agentEnabled: Boolean = false,
        installedSkills: Set<String> = emptySet(),
    ) {
        composeRule.setContent {
            KimiTheme(darkTheme = dark, dynamicColor = false) {
                var input by remember { mutableStateOf("") }
                ChatScreenContent(
                    messages = messages,
                    isStreaming = isStreaming,
                    isConnected = true,
                    input = input,
                    onInputChange = { input = it },
                    onSend = {},
                    onOpenSettings = {},
                    contextState = contextState,
                    agentEnabled = agentEnabled,
                    installedSkills = installedSkills,
                    onOpenMarketplace = {},
                )
            }
        }
        capture(name)
    }

    private val sampleCodeAnswer = """
        Here's how to reverse a string in Python:

        ```python
        def reverse(s: str) -> str:
            return s[::-1]

        # Test it
        print(reverse("hello"))  # olleh
        ```

        The slice `[::-1]` steps through the string backwards. For **large input**,
        it's also the *fastest* approach — no extra allocations beyond the result.
    """.trimIndent()

    @Test
    fun chatEmptyLight() = setChatContent(emptyList(), name = "chat_empty_light")

    @Test
    fun chatEmptyDark() = setChatContent(emptyList(), dark = true, name = "chat_empty_dark")

    @Test
    fun chatMarkdownLight() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Write a Python function that reverses a string"),
            ChatMessage(role = MessageRole.ASSISTANT, content = sampleCodeAnswer),
        ),
        name = "chat_markdown_light",
    )

    @Test
    fun chatMarkdownDark() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Write a Python function that reverses a string"),
            ChatMessage(role = MessageRole.ASSISTANT, content = sampleCodeAnswer),
        ),
        dark = true,
        name = "chat_markdown_dark",
    )

    @Test
    fun chatStreaming() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Explain quantum computing simply"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true),
        ),
        isStreaming = true,
        name = "chat_streaming",
    )

    // ---- Context ring & agent chrome ----

    @Test
    fun chatContextRingMid() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Explain quantum computing simply"),
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Quantum computing uses qubits, which can be in superposition…",
            ),
        ),
        contextState = ContextState(tokens = 524_288, maxTokens = 1_048_576, pct = 0.5, messageCount = 2),
        name = "chat_context_ring_mid",
    )

    @Test
    fun chatContextRingHighDark() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Keep going"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Sure — here is the next section…"),
        ),
        dark = true,
        contextState = ContextState(tokens = 900_000, maxTokens = 1_048_576, pct = 0.86, messageCount = 2),
        name = "chat_context_ring_high_dark",
    )

    @Test
    fun chatAgentMode() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Find the latest Kimi K3 release notes"),
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Let me search for that.\n\n```\nTOOL_CALL web_search: Kimi K3 release notes\n→ 1. Kimi K3: Moonshot's largest model…\n```\n\nHere's what I found…",
                streaming = true,
            ),
        ),
        isStreaming = true,
        agentEnabled = true,
        installedSkills = SkillEngine.all.map { it.id }.toSet(),
        name = "chat_agent_mode",
    )

    @Test
    fun chatCompactedNotice() = setChatContent(
        listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = "Earlier: discussed project setup, agreed on Kotlin + Compose, wrote the networking layer, and set up CI. Key decisions preserved below.",
                notice = true,
            ),
            ChatMessage(role = MessageRole.USER, content = "Continue with the UI work"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "Next up: the theme. I'll go with a warm palette…"),
        ),
        name = "chat_compacted_notice",
    )

    // ---- Marketplace ----

    @Test
    fun marketplaceLight() {
        val installed = setOf("web_search", "mcp_github")
        composeRule.setContent {
            KimiTheme(dynamicColor = false) {
                MarketplaceScreenContent(
                    items = Marketplace.catalog,
                    installed = installed,
                    query = "",
                    category = null,
                    categories = Marketplace.categories(),
                    onQueryChange = {},
                    onCategoryChange = {},
                    onToggle = {},
                    onBack = {},
                )
            }
        }
        capture("marketplace_light")
    }

    @Test
    fun marketplaceConnectorsDark() {
        val connectors = Marketplace.catalog.filter { it.type == CatalogType.CONNECTOR }
        composeRule.setContent {
            KimiTheme(darkTheme = true, dynamicColor = false) {
                MarketplaceScreenContent(
                    items = connectors,
                    installed = setOf("mcp_fetch", "mcp_memory", "mcp_context7"),
                    query = "",
                    category = null,
                    categories = Marketplace.categories(),
                    onQueryChange = {},
                    onCategoryChange = {},
                    onToggle = {},
                    onBack = {},
                )
            }
        }
        capture("marketplace_connectors_dark")
    }
}