package com.tosin.docprocessor.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import java.util.TreeSet

/**
 * Maps between the parser's [TextSpan] list and Compose [AnnotatedString].
 * Text-position sizes follow UTF-16 code units, which both models use.
 */
object AnnotatedStringMappers {

    fun TextSpan.toSpanStyle(): SpanStyle = SpanStyle(
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            isUnderline && isStrikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            isUnderline -> TextDecoration.Underline
            isStrikethrough -> TextDecoration.LineThrough
            else -> null
        },
        fontSize = fontSize?.sp ?: TextUnit.Unspecified,
        color = parseColor(color)
    )

    fun spansToAnnotatedString(spans: List<TextSpan>): AnnotatedString = buildAnnotatedString {
        spans.forEach { span ->
            pushStyle(span.toSpanStyle())
            append(span.text)
            pop()
        }
    }

    /**
     * Re-derives the span list from an edited [AnnotatedString]. The IME keeps
     * style ranges aligned as text changes, so reading the styled runs back is a
     * faithful, order-preserving conversion.
     */
    fun AnnotatedString.toTextSpans(): List<TextSpan> {
        if (text.isEmpty()) return emptyList()

        val bounds = TreeSet<Int>()
        bounds.add(0)
        bounds.add(text.length)
        for (range in spanStyles) {
            bounds.add(range.start.coerceIn(0, text.length))
            bounds.add(range.end.coerceIn(0, text.length))
        }

        val points = bounds.toList()
        return buildList {
            for (index in 0 until points.size - 1) {
                val start = points[index]
                val end = points[index + 1]
                if (end <= start) continue
                val style = spanStyles.asReversed()
                    .firstOrNull { it.start <= start && end <= it.end }
                    ?.item
                add(style.toTextSpan(text.substring(start, end)))
            }
        }
    }

    fun parseColor(hex: String): Color {
        val normalized = hex.trim().removePrefix("#")
        return runCatching {
            val argb = when (normalized.length) {
                6 -> (0xFF000000 or normalized.toLong(16)).toInt()
                8 -> normalized.toLong(16).toInt()
                else -> Color.Black.toArgb()
            }
            Color(argb)
        }.getOrDefault(Color.Black)
    }

    private fun hexToColor(hex: String): String {
        val normalized = hex.trim().removePrefix("#")
        return when (normalized.length) {
            6, 8 -> normalized.uppercase()
            else -> "000000"
        }
    }

    private fun SpanStyle?.toTextSpan(text: String): TextSpan {
        if (this == null) return TextSpan(text = text, color = "000000")
        val decoration = textDecoration
        return TextSpan(
            text = text,
            isBold = (fontWeight?.weight ?: FontWeight.Normal.weight) >= 700,
            isItalic = fontStyle == FontStyle.Italic,
            isUnderline = decoration != null && decoration.contains(TextDecoration.Underline),
            isStrikethrough = decoration != null && decoration.contains(TextDecoration.LineThrough),
            color = hexToColor(colorToString(color))
        )
    }

    private fun colorToString(color: Color?): String {
        if (color == null) return "000000"
        return String.format("%08X", color.toArgb())
    }
}