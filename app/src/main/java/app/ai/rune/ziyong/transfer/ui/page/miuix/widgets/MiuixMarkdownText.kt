// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Minimal Markdown renderer for release-notes style text.
 *
 * Kept dependency-free on purpose (GitHub release bodies are the primary input):
 * - headings: `#` / `##` / `###`
 * - unordered lists: `-` / `*`
 * - ordered lists: `1.`
 * - blockquotes: `>`
 * - fenced code blocks: ``` ```
 * - inline: `**bold**`, `*italic*`, `` `code` ``, `[label](url)` (rendered as plain label)
 *
 * Unknown constructs degrade to plain text; the component never crashes on input.
 */
@Composable
fun MiuixMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body2,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val onSurface = MiuixTheme.colorScheme.onSurface
    val linkColor = MiuixTheme.colorScheme.primary
    val codeColor = MiuixTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val factor = when (block.level) {
                        1 -> 1.4f
                        2 -> 1.2f
                        else -> 1.05f
                    }
                    Text(
                        text = buildInline(block.content, linkColor, codeColor),
                        style = style.copy(
                            fontSize = style.fontSize * factor,
                            lineHeight = style.lineHeight * factor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = onSurface,
                    )
                }

                is MarkdownBlock.Bullet -> MarkdownListItem(
                    marker = "•",
                    content = block.content,
                    style = style,
                    color = color,
                    linkColor = linkColor,
                    codeColor = codeColor,
                )

                is MarkdownBlock.Numbered -> MarkdownListItem(
                    marker = "${block.index}.",
                    content = block.content,
                    style = style,
                    color = color,
                    linkColor = linkColor,
                    codeColor = codeColor,
                )

                is MarkdownBlock.Quote -> Text(
                    text = buildInline(block.content, linkColor, codeColor),
                    style = style.copy(fontStyle = FontStyle.Italic),
                    color = color.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                )

                is MarkdownBlock.Code -> Text(
                    text = block.content,
                    style = style.copy(fontFamily = FontFamily.Monospace),
                    color = codeColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )

                is MarkdownBlock.Paragraph -> Text(
                    text = buildInline(block.content, linkColor, codeColor),
                    style = style,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun MarkdownListItem(
    marker: String,
    content: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    codeColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = marker,
            style = style,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = buildInline(content, linkColor, codeColor),
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: String) : MarkdownBlock
    data class Paragraph(val content: String) : MarkdownBlock
    data class Bullet(val content: String) : MarkdownBlock
    data class Numbered(val index: Int, val content: String) : MarkdownBlock
    data class Quote(val content: String) : MarkdownBlock
    data class Code(val content: String) : MarkdownBlock
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeLines = mutableListOf<String>()
    var inCodeBlock = false

    fun flushCode() {
        if (codeLines.isNotEmpty()) {
            blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
            codeLines.clear()
        }
    }

    markdown.lineSequence().forEach { rawLine ->
        // Trim trailing whitespace only; keep leading indentation inside code fences.
        val line = rawLine.trimEnd()
        if (line.startsWith("```")) {
            if (inCodeBlock) {
                flushCode()
                inCodeBlock = false
            } else {
                flushCode()
                inCodeBlock = true
            }
            return@forEach
        }
        if (inCodeBlock) {
            codeLines += line
            return@forEach
        }
        if (line.isBlank()) {
            return@forEach
        }
        val heading = HEADING_REGEX.matchEntire(line)
        if (heading != null) {
            blocks += MarkdownBlock.Heading(
                level = heading.groupValues[1].length.coerceAtMost(3),
                content = heading.groupValues[2].trim(),
            )
            return@forEach
        }
        val bullet = BULLET_REGEX.matchEntire(line)
        if (bullet != null) {
            blocks += MarkdownBlock.Bullet(bullet.groupValues[1].trim())
            return@forEach
        }
        val numbered = NUMBERED_REGEX.matchEntire(line)
        if (numbered != null) {
            blocks += MarkdownBlock.Numbered(
                index = numbered.groupValues[1].toIntOrNull() ?: 1,
                content = numbered.groupValues[2].trim(),
            )
            return@forEach
        }
        val quote = QUOTE_REGEX.matchEntire(line)
        if (quote != null) {
            blocks += MarkdownBlock.Quote(quote.groupValues[1].trim())
            return@forEach
        }
        blocks += MarkdownBlock.Paragraph(line)
    }
    if (inCodeBlock) {
        flushCode()
    }
    return blocks
}

private fun buildInline(
    text: String,
    linkColor: Color,
    codeColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val char = text[i]
        when {
            char == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                        append(text.substring(i + 1, close))
                    }
                    i = close + 1
                } else {
                    append(char)
                    i++
                }
            }

            char == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val openParen = if (closeBracket != -1) text.indexOf('(', closeBracket + 1) else -1
                val closeParen = if (openParen != -1) text.indexOf(')', openParen + 1) else -1
                if (openParen == closeBracket + 1 && closeParen != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = linkColor)) {
                        append(text.substring(i + 1, closeBracket))
                    }
                    i = closeParen + 1
                } else {
                    append(char)
                    i++
                }
            }

            char == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val close = text.indexOf("**", i + 2)
                if (close != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, close))
                    }
                    i = close + 2
                } else {
                    append(char)
                    i++
                }
            }

            char == '*' -> {
                val close = text.indexOf('*', i + 1)
                if (close != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, close))
                    }
                    i = close + 1
                } else {
                    append(char)
                    i++
                }
            }

            else -> {
                append(char)
                i++
            }
        }
    }
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET_REGEX = Regex("""^[-*]\s+(.*)$""")
private val NUMBERED_REGEX = Regex("""^(\d+)\.\s+(.*)$""")
private val QUOTE_REGEX = Regex("""^>\s?(.*)$""")