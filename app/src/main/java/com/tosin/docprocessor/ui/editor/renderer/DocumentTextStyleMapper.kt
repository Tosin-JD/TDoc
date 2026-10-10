package com.tosin.docprocessor.ui.editor.renderer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.typography.FontFamilyResolver
import com.tosin.docprocessor.domain.typography.TextFlowResolver
import com.tosin.docprocessor.ui.editor.renderer.model.ParagraphTextModel
import com.tosin.docprocessor.ui.theme.toComposeFontFamily

/**
 * Maps a `DocumentElement.Paragraph` plus a base [TextStyle] into an immutable
 * [ParagraphTextModel].
 *
 * Owns three concerns that were previously inline and duplicated:
 * - anti-clipping line height derived from the tallest run ([TextFlowResolver]);
 * - font fallback mapping per run ([FontFamilyResolver]);
 * - per-run styling (weight, style, colour, size, baseline shift, spacing).
 *
 * It is pure presentation logic: no composable state, no I/O.
 */
class DocumentTextStyleMapper(
    private val flowResolver: TextFlowResolver = TextFlowResolver(),
    private val fontResolver: FontFamilyResolver = FontFamilyResolver()
) {

    fun map(
        paragraph: DocumentElement.Paragraph,
        baseStyle: TextStyle,
        isDarkMode: Boolean
    ): ParagraphTextModel {
        val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
        val baseSizeSp = baseStyle.fontSize.value.let { if (it.isFinite()) it else DEFAULT_FONT_SIZE_SP }
        val maxSizeSp = paragraph.spans
            .mapNotNull { it.fontSize?.toFloat() }
            .maxOrNull()
            ?.coerceAtLeast(baseSizeSp)
            ?: baseSizeSp
        val lineHeightSp = flowResolver.effectiveLineHeightPt(
            spacing = paragraph.style.spacing,
            baseFontSizePt = baseSizeSp,
            maxFontSizePt = maxSizeSp
        )

        val textStyle = baseStyle.copy(
            lineHeight = lineHeightSp.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None
            )
        )

        val annotated = buildAnnotatedString {
            paragraph.listLabel?.let { label ->
                withStyle(SpanStyle(color = Color(tokens.contentText))) {
                    append(label)
                    append(" ")
                }
            }
            paragraph.spans.forEach { span ->
                withStyle(span.toSpanStyle(tokens, isDarkMode)) { append(span.text) }
            }
        }

        return ParagraphTextModel(
            text = annotated,
            textStyle = textStyle,
            textAlign = paragraph.style.alignment.toTextAlign(),
            indentStart = paragraph.style.indentation.left.twipsToDp(),
            indentEnd = paragraph.style.indentation.right.twipsToDp(),
            spaceBefore = paragraph.style.spacing.before.twipsToDp(),
            spaceAfter = paragraph.style.spacing.after.twipsToDp()
        )
    }

    private fun TextSpan.toSpanStyle(tokens: DocumentColorTokens, isDarkMode: Boolean): SpanStyle {
        val resolvedColor = Color(DocumentTextColorResolver.resolveTextColor(color, tokens, isDarkMode))
        val background = highlightColor
            ?.let { DocumentTextColorResolver.parseColorString(it) }
            ?.let { Color(it) }
            ?: Color.Unspecified
        val decoration = when {
            isUnderline && isStrikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            isUnderline -> TextDecoration.Underline
            isStrikethrough -> TextDecoration.LineThrough
            else -> null
        }

        return SpanStyle(
            color = resolvedColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = fontResolver.resolve(fontFamily).toComposeFontFamily(),
            fontSize = fontSize?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            textDecoration = decoration,
            background = background,
            letterSpacing = characterSpacing?.let { (it / TWIPS_PER_POINT).sp }
                ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            baselineShift = when {
                isSuperscript -> BaselineShift.Superscript
                isSubscript -> BaselineShift.Subscript
                else -> null
            }
        )
    }

    private fun ParagraphAlignment?.toTextAlign(): TextAlign = when (this) {
        ParagraphAlignment.END -> TextAlign.End
        ParagraphAlignment.CENTER -> TextAlign.Center
        ParagraphAlignment.JUSTIFIED, ParagraphAlignment.DISTRIBUTED -> TextAlign.Justify
        else -> TextAlign.Start
    }

    private fun Int?.twipsToDp() = ((this ?: 0) / TWIPS_PER_POINT).dp

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 16f
        const val TWIPS_PER_POINT = 20f
    }
}
