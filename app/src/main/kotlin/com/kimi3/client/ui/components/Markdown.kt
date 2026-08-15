package com.kimi3.client.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal markdown renderer for AI responses.
 * Supports: fenced code blocks (with copy), inline code, bold, italic, headings, bullets, links, quotes.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val inline: List<InlineSpan>) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
}

sealed interface InlineSpan {
    data class Text(val text: String) : InlineSpan
    data class Bold(val text: String) : InlineSpan
    data class Italic(val text: String) : InlineSpan
    data class InlineCode(val text: String) : InlineSpan
    data class Link(val text: String, val url: String) : InlineSpan
}

fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val codeFence = Regex("^```(\\w*)\\s*$").find(line.trim())
        if (codeFence != null) {
            val lang = codeFence.groupValues[1].ifBlank { null }
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append("\n")
                i++
            }
            i++ // skip closing fence
            blocks.add(MarkdownBlock.Code(lang, code.toString().trimEnd('\n')))
            continue
        }
        if (line.trim().isEmpty()) {
            i++
            continue
        }
        val heading = Regex("^(#{1,4})\\s+(.+)$").find(line.trim())
        if (heading != null) {
            blocks.add(MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2]))
            i++
            continue
        }
        if (Regex("^[-*]\\s+").containsMatchIn(line.trim()) || Regex("^\\d+\\.\\s+").containsMatchIn(line.trim())) {
            val items = mutableListOf<String>()
            while (i < lines.size && (Regex("^[-*]\\s+").containsMatchIn(lines[i].trim()) || Regex("^\\d+\\.\\s+").containsMatchIn(lines[i].trim()))) {
                items.add(lines[i].trim().replaceFirst(Regex("^([-*]|\\d+\\.)\\s+"), ""))
                i++
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }
        if (line.trim().startsWith(">")) {
            val quote = StringBuilder()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quote.append(lines[i].trim().removePrefix(">").trim()).append("\n")
                i++
            }
            blocks.add(MarkdownBlock.Quote(quote.toString().trim()))
            continue
        }
        // paragraph: gather until blank line
        val para = StringBuilder()
        while (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].trim().startsWith("```")) {
            para.append(lines[i].trim()).append(" ")
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(parseInline(para.toString().trim())))
    }
    return blocks
}

private fun parseInline(text: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    var i = 0
    val s = text
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i) {
                    spans.add(InlineSpan.Bold(s.substring(i + 2, end)))
                    i = end + 2
                } else {
                    spans.add(InlineSpan.Text(s.substring(i, i + 1))); i++
                }
            }
            s.startsWith("`", i) -> {
                val end = s.indexOf("`", i + 1)
                if (end > i) {
                    spans.add(InlineSpan.InlineCode(s.substring(i + 1, end)))
                    i = end + 1
                } else {
                    spans.add(InlineSpan.Text(s.substring(i, i + 1))); i++
                }
            }
            s.startsWith("*", i) -> {
                val end = s.indexOf("*", i + 1)
                if (end > i) {
                    spans.add(InlineSpan.Italic(s.substring(i + 1, end)))
                    i = end + 1
                } else {
                    spans.add(InlineSpan.Text(s.substring(i, i + 1))); i++
                }
            }
            s.startsWith("[", i) -> {
                val close = s.indexOf("]", i)
                val openParen = s.indexOf("(", close)
                val closeParen = s.indexOf(")", openParen)
                if (close > i && openParen == close + 1 && closeParen > openParen) {
                    spans.add(InlineSpan.Link(s.substring(i + 1, close), s.substring(openParen + 1, closeParen)))
                    i = closeParen + 1
                } else {
                    spans.add(InlineSpan.Text(s.substring(i, i + 1))); i++
                }
            }
            else -> {
                spans.add(InlineSpan.Text(s.substring(i, i + 1))); i++
            }
        }
    }
    return spans
}

private fun inlineToAnnotated(spans: List<InlineSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is InlineSpan.Text -> append(span.text)
            is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(span.text) }
            is InlineSpan.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                )
            ) { append(span.text) }
            is InlineSpan.Link -> {
                pushStringAnnotation(tag = "URL", annotation = span.url)
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                ) { append(span.text) }
                pop()
            }
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseMarkdown(markdown).forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = inlineToAnnotated(block.inline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = block.text,
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        block.items.forEach { item ->
                            Text(
                                text = "•  ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = inlineToAnnotated(parseInline(item)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                is MarkdownBlock.Quote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                is MarkdownBlock.Code -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(codeBackground, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = block.language ?: "code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", block.code))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = block.code,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
