package com.tosin.docprocessor.domain.pagination

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan

/**
 * Measures text dimensions using Android's low-level StaticLayout.
 * This is significantly faster and more accurate than using Views.
 */
class TextMeasurer(
    private val context: Context,
    private val unitConverter: UnitConverter
) {
    private val textPaintCache = mutableMapOf<String, TextPaint>()

    fun measureParagraph(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float
    ): Float {
        val layout = buildParagraphLayout(spans, style, widthPt) ?: return 0f
        return unitConverter.pxToPt(layout.height.toFloat())
    }

    fun measurePlainText(
        text: String,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: String? = null,
        widthPt: Float
    ): Float {
        val layout = buildPlainTextLayout(
            text = text,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily,
            widthPt = widthPt
        ) ?: return 0f

        return unitConverter.pxToPt(layout.height.toFloat())
    }

    fun getLastFittingLine(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float,
        maxHeightPt: Float
    ): Int {
        val layout = buildParagraphLayout(spans, style, widthPt) ?: return -1
        val maxHeightPx = unitConverter.ptToPx(maxHeightPt).toInt().coerceAtLeast(0)
        return layout.getLineForVertical(maxHeightPx)
    }

    fun buildParagraphLayout(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float
    ): StaticLayout? {
        if (spans.isEmpty()) return null

        val text = spans.joinToString("") { it.text }
        if (text.isEmpty()) return null

        return buildLayout(
            text = text,
            paint = createTextPaint(spans.first()),
            widthPt = widthPt,
            alignment = alignmentToLayout(style.alignment),
            lineSpacingPx = unitConverter.ptToPx(style.spacing.line?.toFloat() ?: 0f)
        )
    }

    fun buildPlainTextLayout(
        text: String,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: String? = null,
        widthPt: Float
    ): StaticLayout? {
        if (text.isEmpty()) return null

        val paint = TextPaint().apply {
            textSize = unitConverter.ptToPx(fontSize)
            typeface = getTypeface(fontFamily, isBold, isItalic)
            color = 0xFF000000.toInt()
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

    private fun createTextPaint(span: TextSpan): TextPaint {
        val cacheKey = "${span.fontFamily}_${span.fontSize}_${span.isBold}_${span.isItalic}_${span.color}"

        return textPaintCache.getOrPut(cacheKey) {
            TextPaint().apply {
                textSize = unitConverter.ptToPx(span.fontSize?.toFloat() ?: 12f)
                typeface = getTypeface(span.fontFamily, span.isBold, span.isItalic)
                color = parseColorString(span.color)
                isAntiAlias = true
                isSubpixelText = true
            }
        }
    }

    private fun getTypeface(fontFamily: String?, isBold: Boolean, isItalic: Boolean): Typeface {
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        return if (fontFamily != null) {
            Typeface.create(fontFamily, style)
        } else {
            Typeface.defaultFromStyle(style)
        }
    }

    private fun parseColorString(colorStr: String): Int {
        return try {
            val hex = if (colorStr.startsWith("#")) colorStr.substring(1) else colorStr
            val fullHex = if (hex.length == 6) "FF$hex" else hex
            fullHex.toLong(16).toInt()
        } catch (_: Exception) {
            0xFF000000.toInt()
        }
    }

    private fun alignmentToLayout(alignment: ParagraphAlignment): Layout.Alignment {
        return when (alignment) {
            ParagraphAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            ParagraphAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
            ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            ParagraphAlignment.JUSTIFIED -> Layout.Alignment.ALIGN_NORMAL
            ParagraphAlignment.DISTRIBUTED -> Layout.Alignment.ALIGN_CENTER
        }
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        widthPt: Float,
        alignment: Layout.Alignment,
        lineSpacingPx: Float = 0f
    ): StaticLayout {
        val widthPx = unitConverter.ptToPx(widthPt).toInt().coerceAtLeast(1)

        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(alignment)
            .setLineSpacing(lineSpacingPx, 1f)
            .setIncludePad(true)
            .build()
    }

    fun clearCache() {
        textPaintCache.clear()
    }
}
