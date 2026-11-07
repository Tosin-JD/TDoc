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
import androidx.compose.ui.text.style.TextIndent
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
        isDarkMode: Boolean,
        scale: Float = 1f,
        includeListLabel: Boolean = true
    ): ParagraphTextModel {
        val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
        val rawBase = baseStyle.fontSize.value.let { if (it.isFinite()) it else DEFAULT_FONT_SIZE_SP }
        val baseSizeSp = rawBase * scale
        val rawMax = paragraph.spans
            .mapNotNull { it.fontSize?.toFloat() }
            .maxOrNull()
            ?.coerceAtLeast(rawBase)
            ?: rawBase
        val maxSizeSp = rawMax * scale
        val rawLineHeight = flowResolver.effectiveLineHeightPt(
            spacing = paragraph.style.spacing,
            baseFontSizePt = rawBase,
            maxFontSizePt = rawMax
        )
        val lineHeightSp = rawLineHeight * scale

        val rawIndentFirst = (paragraph.style.indentation.firstLine
            ?: paragraph.style.indentation.hanging?.let { -it })?.twipsToSp() ?: 0.sp
        val textStyle = baseStyle.copy(
            fontSize = baseSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            textIndent = TextIndent(
                firstLine = rawIndentFirst * scale,
                restLine = 0.sp
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None
            )
        )

        val annotated = buildAnnotatedString {
            if (includeListLabel) {
                paragraph.listLabel?.let { label ->
                    withStyle(SpanStyle(color = Color(tokens.contentText), fontSize = baseSizeSp.sp)) {
                        append(label)
                        append(" ")
                    }
                }
            }
            paragraph.spans.forEach { span ->
                withStyle(span.toSpanStyle(tokens, isDarkMode, scale)) { append(span.text) }
            }
        }

        return ParagraphTextModel(
            text = annotated,
            textStyle = textStyle,
            textAlign = paragraph.style.alignment.toTextAlign(),
            indentStart = paragraph.style.indentation.left.twipsToDp() * scale,
            indentEnd = paragraph.style.indentation.right.twipsToDp() * scale,
            spaceBefore = paragraph.style.spacing.before.twipsToDp() * scale,
            spaceAfter = paragraph.style.spacing.after.twipsToDp() * scale
        )
    }

    private fun TextSpan.toSpanStyle(
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        scale: Float
    ): SpanStyle {
        // Effective background behind this run: own highlight wins, then the
        // run's shading, then the page. Text colour is contrast-checked
        // against it so authored colours stay readable on both themes.
        val highlightArgb = highlightColor
            ?.let { DocumentTextColorResolver.resolveHighlightColor(it, tokens, isDarkMode, tokens.pageBackground) }
        val shadingArgb = shading?.fill
            ?.let { DocumentTextColorResolver.resolveBackgroundColor(it, tokens, isDarkMode, tokens.pageBackground) }
        val backgroundArgb = highlightArgb ?: shadingArgb ?: tokens.pageBackground
        // Unstyled links take the themed link colour (same convention as the
        // print renderer); authored colours still win.
        val effectiveColor = if (hyperlink != null && color == null) null else color
        val resolvedColor = if (hyperlink != null && color == null) {
            Color(tokens.linkColor)
        } else {
            Color(
                DocumentTextColorResolver.resolveTextColor(effectiveColor, tokens, isDarkMode, backgroundArgb)
            )
        }
        val background = (highlightArgb ?: shadingArgb)?.let { Color(it) } ?: Color.Unspecified
        val decoration = when {
            isUnderline && isStrikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            isUnderline || hyperlink != null -> TextDecoration.Underline
            isStrikethrough -> TextDecoration.LineThrough
            else -> null
        }

        return SpanStyle(
            color = resolvedColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = fontResolver.resolve(fontFamily).toComposeFontFamily(),
            fontSize = fontSize?.let { (it * scale).sp } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            textDecoration = decoration,
            background = background,
            letterSpacing = characterSpacing?.let { ((it / TWIPS_PER_POINT) * scale).sp }
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
    private fun Int?.twipsToSp() = ((this ?: 0) / TWIPS_PER_POINT).sp

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 16f
        const val TWIPS_PER_POINT = 20f
    }
}
