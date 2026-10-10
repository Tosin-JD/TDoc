package com.tosin.docprocessor.domain.pagination

import android.content.Context
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.core.text.AndroidTypefaceMapper
import com.tosin.docprocessor.core.text.SpannableTextBuilder
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.layout.ElementIndentation
import com.tosin.docprocessor.domain.typography.FontFamilyResolver
import com.tosin.docprocessor.domain.typography.ResolvedLineHeight
import com.tosin.docprocessor.domain.typography.TextFlowResolver

/**
 * Measures text dimensions using Android's low-level StaticLayout.
 * This is significantly faster and more accurate than using Views.
 */
class TextMeasurer(
    private val context: Context,
    private val unitConverter: UnitConverter,
    private val fontResolver: FontFamilyResolver = FontFamilyResolver(),
    private val flowResolver: TextFlowResolver = TextFlowResolver()
) {
    fun measureParagraph(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float,
        indentation: ElementIndentation = ElementIndentation(),
        isDarkMode: Boolean = false
    ): Float {
        val layout = buildParagraphLayout(spans, style, widthPt, indentation, isDarkMode) ?: return 0f
        return unitConverter.pxToPt(layout.height.toFloat())
    }

    fun measurePlainText(
        text: String,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: String? = null,
        widthPt: Float,
        isDarkMode: Boolean = false
    ): Float {
        val layout = buildPlainTextLayout(
            text = text,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily,
            widthPt = widthPt,
            isDarkMode = isDarkMode
        ) ?: return 0f

        return unitConverter.pxToPt(layout.height.toFloat())
    }

    fun getLastFittingLine(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float,
        maxHeightPt: Float,
        indentation: ElementIndentation = ElementIndentation(),
        isDarkMode: Boolean = false
    ): Int {
        val layout = buildParagraphLayout(spans, style, widthPt, indentation, isDarkMode) ?: return -1
        val maxHeightPx = unitConverter.ptToPx(maxHeightPt).toInt().coerceAtLeast(0)
        var line = layout.getLineForVertical(maxHeightPx)

        while (line >= 0 && layout.getLineBottom(line) > maxHeightPx) {
            line--
        }

        return line
    }

    fun buildParagraphLayout(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float,
        indentation: ElementIndentation = ElementIndentation(),
        isDarkMode: Boolean = false
    ): StaticLayout? {
        if (spans.isEmpty()) return null

        val effectiveWidth = (widthPt - indentation.leftPt - indentation.rightPt)
            .coerceAtLeast(72f)

        val baseSizePt = spans.mapNotNull { it.fontSize?.toFloat() }.maxOrNull() ?: DEFAULT_FONT_SIZE_PT
        val tokens = tokens(isDarkMode)
        val text = SpannableTextBuilder.build(
            spans = spans,
            baseSizePt = baseSizePt,
            fontResolver = fontResolver,
            tokens = tokens,
            isDarkMode = isDarkMode,
            sizeToPx = { pt -> unitConverter.ptToPx(pt).toInt().coerceAtLeast(1) }
        )
        if (text.isEmpty()) return null

        val paint = createBasePaint(baseSizePt, isDarkMode)
        val lineSpacing = lineSpacingFor(flowResolver.resolveLineHeight(style.spacing), paint)

        return buildLayout(
            text = text,
            paint = paint,
            widthPt = effectiveWidth,
            alignment = alignmentToLayout(style.alignment),
            lineSpacingPx = lineSpacing.first,
            lineSpacingMultiplier = lineSpacing.second,
            justificationMode = justificationModeFor(style.alignment)
        )
    }

    fun buildPlainTextLayout(
        text: String,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: String? = null,
        widthPt: Float,
        isDarkMode: Boolean = false
    ): StaticLayout? {
        if (text.isEmpty()) return null

        val paint = TextPaint().apply {
            textSize = unitConverter.ptToPx(fontSize)
            typeface = AndroidTypefaceMapper.map(fontResolver.resolve(fontFamily), isBold, isItalic)
            color = DocumentTextColorResolver.resolvePlainTextColor(tokens(isDarkMode), isDarkMode)
            isAntiAlias = true
            isSubpixelText = true
        }

        return buildLayout(
            text = text,
            paint = paint,
            widthPt = widthPt,
            alignment = Layout.Alignment.ALIGN_NORMAL
        )
    }

    fun getLineStart(layout: StaticLayout, lineNumber: Int): Int {
        return if (lineNumber < layout.lineCount) layout.getLineStart(lineNumber) else layout.text.length
    }

    fun getLineEnd(layout: StaticLayout, lineNumber: Int): Int {
        return if (lineNumber < layout.lineCount) layout.getLineEnd(lineNumber) else layout.text.length
    }

    fun clearCache() {
        AndroidTypefaceMapper.clearCache()
    }

    private fun tokens(isDarkMode: Boolean): DocumentColorTokens =
        if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light

    private fun createBasePaint(sizePt: Float, isDarkMode: Boolean): TextPaint = TextPaint().apply {
        textSize = unitConverter.ptToPx(sizePt)
        typeface = AndroidTypefaceMapper.map(fontResolver.resolve(null), false, false)
        color = DocumentTextColorResolver.resolvePlainTextColor(tokens(isDarkMode), isDarkMode)
        isAntiAlias = true
        isSubpixelText = true
    }

    /**
     * Translates a resolved line height into the pair consumed by
     * [StaticLayout.Builder.setLineSpacing]. Exact/AT_LEAST rules become an
     * additive gap so text never renders shorter than the document requested.
     */
    private fun lineSpacingFor(resolved: ResolvedLineHeight, paint: TextPaint): Pair<Float, Float> =
        when (resolved) {
            is ResolvedLineHeight.Multiplier -> 0f to resolved.multiplier
            is ResolvedLineHeight.Exact -> {
                val metrics = paint.fontMetrics
                val naturalPx = metrics.descent - metrics.ascent + metrics.leading
                val targetPx = unitConverter.ptToPx(resolved.points)
                (targetPx - naturalPx).coerceAtLeast(0f) to 1f
            }
            ResolvedLineHeight.Unspecified -> 0f to 1f
        }

    private fun alignmentToLayout(alignment: ParagraphAlignment): Layout.Alignment {
        return when (alignment) {
            ParagraphAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            ParagraphAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
            ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            ParagraphAlignment.JUSTIFIED, ParagraphAlignment.DISTRIBUTED -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    @Suppress("DEPRECATION")
    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPt: Float,
        alignment: Layout.Alignment,
        lineSpacingPx: Float = 0f,
        lineSpacingMultiplier: Float = 1f,
        justificationMode: Int = Layout.JUSTIFICATION_MODE_NONE
    ): StaticLayout {
        val widthPx = unitConverter.ptToPx(widthPt).toInt().coerceAtLeast(1)

        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacingPx, lineSpacingMultiplier)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setIncludePad(false)

        if (justificationMode != Layout.JUSTIFICATION_MODE_NONE) {
            builder.setJustificationMode(justificationMode)
        }

        return builder.build()
    }

    private companion object {
        const val DEFAULT_FONT_SIZE_PT = 12f
    }
}

/**
 * Maps a paragraph alignment to a `StaticLayout` justification mode. Kept as a
 * pure function so the mapping can be unit-tested without an Android device.
 */
@Suppress("DEPRECATION")
internal fun justificationModeFor(alignment: ParagraphAlignment): Int = when (alignment) {
    ParagraphAlignment.JUSTIFIED, ParagraphAlignment.DISTRIBUTED -> Layout.JUSTIFICATION_MODE_INTER_WORD
    else -> Layout.JUSTIFICATION_MODE_NONE
}
