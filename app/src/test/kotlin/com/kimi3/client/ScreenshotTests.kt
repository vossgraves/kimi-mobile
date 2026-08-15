package com.kimi3.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.screenshot.Screenshot
import com.kimi3.client.ui.ChatMessage
import com.kimi3.client.ui.MessageRole
import com.kimi3.client.ui.screens.ChatScreenContent
import com.kimi3.client.ui.theme.KimiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshot tests — rendered by the compose-screenshot plugin (Robolectric).
 * Artifacts land in app/screenshots/debug; verify visually in CI.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTests {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setChatContent(
        messages: List<ChatMessage>,
        isStreaming: Boolean = false,
        dark: Boolean = false,
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
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()
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
    @Screenshot(name = "chat_empty_light")
    fun chatEmptyLight() = setChatContent(emptyList())

    @Test
    @Screenshot(name = "chat_empty_dark")
    fun chatEmptyDark() = setChatContent(emptyList(), dark = true)

    @Test
    @Screenshot(name = "chat_markdown_code_light")
    fun chatMarkdownLight() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Write a Python function that reverses a string"),
            ChatMessage(role = MessageRole.ASSISTANT, content = sampleCodeAnswer),
        )
    )

    @Test
    @Screenshot(name = "chat_markdown_code_dark")
    fun chatMarkdownDark() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Write a Python function that reverses a string"),
            ChatMessage(role = MessageRole.ASSISTANT, content = sampleCodeAnswer),
        ),
        dark = true,
    )

    @Test
    @Screenshot(name = "chat_streaming_light")
    fun chatStreamingLight() = setChatContent(
        listOf(
            ChatMessage(role = MessageRole.USER, content = "Explain quantum computing simply"),
            ChatMessage(role = MessageRole.ASSISTANT, content = "", streaming = true),
        ),
        isStreaming = true,
    )
}