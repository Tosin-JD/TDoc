package com.tosin.docprocessor.ui.editor.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import java.util.Locale

object AnnotatedStringMappers {
    fun DocumentElement.Paragraph.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
        listLabel?.let {
            append(it)
            append(" ")
        }
        spans.forEach { span ->
            withStyle(span.toComposeStyle()) { append(span.text) }
        }
    }

    /**
     * Builds an [AnnotatedString] from raw document spans. Used by rich content
     * surfaces (tables, headers) that are not a [DocumentElement.Paragraph].
     */
    fun spansToAnnotatedString(spans: List<TextSpan>): AnnotatedString =
        spansToAnnotatedString(spans, DocumentColorTokens.Light, isDarkMode = false)

    /**
     * Theme-aware variant: run colours/highlights are contrast-resolved against
     * [backgroundArgb] (the effective local background — e.g. a table cell fill)
     * so authored colours stay readable in both light and dark mode.
     */
    fun spansToAnnotatedString(
        spans: List<TextSpan>,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): AnnotatedString = buildAnnotatedString {
        spans.forEach { span ->
            withStyle(span.toComposeStyle(tokens, isDarkMode, backgroundArgb)) { append(span.text) }
        }
    }

    fun toTextSpans(
        annotatedString: AnnotatedString,
        originalSpans: List<TextSpan>,
        listLabel: String? = null
    ): List<TextSpan> {
        val labelPrefix = listLabel?.let { "$it " }
        val startOffset = if (
            labelPrefix != null && annotatedString.text.startsWith(labelPrefix)
        ) {
            labelPrefix.length
        } else {
            0
        }
        val endOffset = annotatedString.length
        if (startOffset == endOffset) {
            return listOf(originalSpans.firstOrNull()?.copy(text = "") ?: TextSpan(""))
        }

        val boundaries = sortedSetOf(startOffset, endOffset)
        annotatedString.spanStyles.forEach { range ->
            if (range.end > startOffset && range.start < endOffset) {
                boundaries += range.start.coerceIn(startOffset, endOffset)
                boundaries += range.end.coerceIn(startOffset, endOffset)
            }
        }

        val positions = boundaries.toList()
        val mapped = mutableListOf<TextSpan>()
        for (index in 0 until positions.lastIndex) {
            val start = positions[index]
            val end = positions[index + 1]
            if (start == end) continue

            val fallback = originalSpanAt(originalSpans, start - startOffset)
            val style = annotatedString.spanStyles
                .filter { it.start <= start && it.end >= end }
                .map { it.item }
            val span = fallback.copy(
                text = annotatedString.text.substring(start, end),
                isBold = style.mapNotNull { it.fontWeight }.lastOrNull()
                    ?.let { it.weight >= FontWeight.SemiBold.weight }
                    ?: fallback.isBold,
                isItalic = style.mapNotNull { it.fontStyle }.lastOrNull()
                    ?.let { it == FontStyle.Italic }
                    ?: fallback.isItalic,
                isUnderline = style.mapNotNull { it.textDecoration }.lastOrNull()
                    ?.let { it.contains(TextDecoration.Underline) }
                    ?: fallback.isUnderline,
                isStrikethrough = style.mapNotNull { it.textDecoration }.lastOrNull()
                    ?.let { it.contains(TextDecoration.LineThrough) }
                    ?: fallback.isStrikethrough,
                isSuperscript = style.mapNotNull { it.baselineShift }.lastOrNull()
                    ?.let { it == BaselineShift.Superscript }
                    ?: fallback.isSuperscript,
                isSubscript = style.mapNotNull { it.baselineShift }.lastOrNull()
                    ?.let { it == BaselineShift.Subscript }
                    ?: fallback.isSubscript,
                fontSize = style.mapNotNull { it.fontSize }.lastOrNull()
                    ?.takeIf { it != TextUnit.Unspecified }
                    ?.value
                    ?: fallback.fontSize,
                fontFamily = style.mapNotNull { it.fontFamily?.toString() }.lastOrNull()
                    ?: fallback.fontFamily,
                color = style.mapNotNull { it.color }.lastOrNull()
                    ?.takeIf { it.isSpecified }
                    ?.toArgb()
                    ?.let { String.format(Locale.ROOT, "%06X", it and 0xFFFFFF) }
                    ?: fallback.color,
                highlightColor = style.mapNotNull { it.background }.lastOrNull()
                    ?.takeIf { it.isSpecified }
                    ?.toArgb()
                    ?.let { String.format(Locale.ROOT, "%06X", it and 0xFFFFFF) }
                    ?: fallback.highlightColor
            )
            if (mapped.lastOrNull()?.sameStyle(span) == true) {
                mapped[mapped.lastIndex] = mapped.last().copy(text = mapped.last().text + span.text)
            } else {
                mapped += span
            }
        }
        return mapped
    }

    fun splitSpans(
        spans: List<TextSpan>,
        splitCharPosition: Int
    ): Pair<List<TextSpan>, List<TextSpan>> {
        val spans1 = mutableListOf<TextSpan>()
        val spans2 = mutableListOf<TextSpan>()

        var charCount = 0
        for (span in spans) {
            val spanEnd = charCount + span.text.length

            when {
                spanEnd <= splitCharPosition -> spans1.add(span)
                charCount >= splitCharPosition -> spans2.add(span)
                else -> {
                    val splitInSpan = splitCharPosition - charCount
                    spans1.add(span.copy(text = span.text.substring(0, splitInSpan)))
                    spans2.add(span.copy(text = span.text.substring(splitInSpan)))
                }
            }

            charCount = spanEnd
        }

        return Pair(spans1, spans2)
    }

    private fun originalSpanAt(spans: List<TextSpan>, index: Int): TextSpan {
        var current = 0
        spans.forEach { span ->
            val end = current + span.text.length
            if (index < end) return span
            current = end
        }
        return spans.lastOrNull() ?: TextSpan("")
    }

    private fun TextSpan.sameStyle(other: TextSpan): Boolean = copy(text = "") == other.copy(text = "")

    private fun TextSpan.toComposeStyle(): SpanStyle =
        toComposeStyle(DocumentColorTokens.Light, isDarkMode = false)

    private fun TextSpan.toComposeStyle(
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): SpanStyle {
        val highlightArgb = highlightColor?.let {
            DocumentTextColorResolver.resolveHighlightColor(it, tokens, isDarkMode, backgroundArgb)
        }
        val effectiveBackground = highlightArgb ?: backgroundArgb
        val resolvedColor = if (hyperlink != null && color == null) {
            tokens.linkColor
        } else {
            DocumentTextColorResolver.resolveTextColor(color, tokens, isDarkMode, effectiveBackground)
        }
        return SpanStyle(
            color = Color(resolvedColor),
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            fontSize = fontSize?.sp ?: TextUnit.Unspecified,
            textDecoration = when {
                isUnderline && isStrikethrough -> TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
                isUnderline || hyperlink != null -> TextDecoration.Underline
                isStrikethrough -> TextDecoration.LineThrough
                else -> null
            },
            background = highlightArgb?.let { Color(it) } ?: Color.Unspecified,
            baselineShift = when {
                isSuperscript -> BaselineShift.Superscript
                isSubscript -> BaselineShift.Subscript
                else -> null
            }
        )
    }
}
