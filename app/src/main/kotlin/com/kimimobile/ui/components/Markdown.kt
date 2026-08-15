package com.kimimobile.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
    data class BoldItalic(val text: String) : InlineSpan
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
        val codeFence = Regex("^```\\s*([\\w+#-]*).*$").find(line.trim())
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
    val buf = StringBuilder()
    var i = 0

    fun flush() {
        if (buf.isNotEmpty()) {
            spans.add(InlineSpan.Text(buf.toString()))
            buf.clear()
        }
    }

    /**
     * Finds a closing token that isn't preceded by a space, so prose like
     * "2 * 3 * 4" and "A * B" stays literal instead of turning italic.
     */
    fun findCloser(token: String, from: Int): Int {
        var j = text.indexOf(token, from)
        while (j != -1) {
            if (j > from && text[j - 1] != ' ') return j
            j = text.indexOf(token, j + 1)
        }
        return -1
    }

    while (i < text.length) {
        if (text.startsWith("***", i)) {
            val end = findCloser("***", i + 3)
            if (end > i) {
                flush()
                spans.add(InlineSpan.BoldItalic(text.substring(i + 3, end)))
                i = end + 3
                continue
            }
        }
        if (text.startsWith("**", i)) {
            val end = findCloser("**", i + 2)
            if (end > i) {
                flush()
                spans.add(InlineSpan.Bold(text.substring(i + 2, end)))
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                flush()
                spans.add(InlineSpan.InlineCode(text.substring(i + 1, end)))
                i = end + 1
                continue
            }
        }
        if (text[i] == '[') {
            val close = text.indexOf(']', i)
            val openParen = if (close >= 0) text.indexOf('(', close) else -1
            val closeParen = if (openParen >= 0) text.indexOf(')', openParen) else -1
            if (close > i && openParen == close + 1 && closeParen > openParen) {
                flush()
                spans.add(
                    InlineSpan.Link(
                        text = text.substring(i + 1, close),
                        url = text.substring(openParen + 1, closeParen),
                    )
                )
                i = closeParen + 1
                continue
            }
        }
        if (text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ') {
            val end = findCloser("*", i + 1)
            if (end > i) {
                flush()
                spans.add(InlineSpan.Italic(text.substring(i + 1, end)))
                i = end + 1
                continue
            }
        }
        // Batch plain characters instead of one span per char.
        buf.append(text[i])
        i++
    }
    flush()
    return spans
}

private fun inlineToAnnotated(
    spans: List<InlineSpan>,
    onSurface: Color,
    primary: Color,
    codeBg: Color,
): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            is InlineSpan.Text -> append(span.text)
            is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            is InlineSpan.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            ) { append(span.text) }
            is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) }
            is InlineSpan.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    color = onSurface,
                    background = codeBg,
                )
            ) { append(span.text) }
            is InlineSpan.Link -> {
                pushStringAnnotation(tag = "URL", annotation = span.url)
                withStyle(
                    SpanStyle(
                        color = primary,
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
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val context = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseMarkdown(markdown).forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = inlineToAnnotated(block.inline, onSurface, primary, codeBg),
                        style = style,
                        color = onSurface,
                    )
                }
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = block.text,
                        style = headingStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        block.items.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "•",
                                    style = style,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = inlineToAnnotated(parseInline(item), onSurface, primary, codeBg),
                                    style = style,
                                    color = onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
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
                    CodeBlock(
                        language = block.language,
                        code = block.code,
                        background = codeBackground,
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("code", block.code))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Code block that collapses. Long snippets, tool traces and agent reports fold
 * away so the conversation stays readable; tapping the header expands them.
 */
@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    background: Color,
    onCopy: () -> Unit,
) {
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val isTrace = language == "tool" || language == "agent"
    // Traces and long snippets start folded; short code stays open.
    var expanded by remember(code) { mutableStateOf(!isTrace && lineCount <= 18) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "code-arrow")

    val label = when (language) {
        "tool" -> "Tool call"
        "agent" -> "Subagent report"
        null, "" -> "code"
        else -> language
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = !expanded }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isTrace) Icons.Default.Build else Icons.Default.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "" else "$lineCount lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp)
                    .rotate(arrow),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
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
