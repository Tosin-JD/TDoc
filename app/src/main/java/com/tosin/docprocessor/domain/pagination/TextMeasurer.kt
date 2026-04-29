package com.tosin.docprocessor.domain.pagination

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import kotlin.math.ceil

/**
 * Measures text dimensions using Android's low-level StaticLayout.
 * This is significantly faster and more accurate than using Views.
 *
 * Handles:
 * - Line wrapping based on container width
 * - Line spacing and paragraph spacing
 * - Font attributes (size, bold, italic, etc.)
 * - Multi-span paragraphs with varying styles
 */
class TextMeasurer(
    private val context: Context,
    private val unitConverter: UnitConverter
) {
    private val textPaintCache = mutableMapOf<String, TextPaint>()

    /**
     * Measure the height of a paragraph in Points.
     *
     * @param spans The text spans that make up the paragraph
     * @param style The paragraph style (alignment, spacing, etc.)
     * @param widthPt The available width in Points
     * @return The total height in Points
     */
    fun measureParagraph(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float
    ): Float {
        if (spans.isEmpty()) {
            return 0f
        }

        val text = spans.joinToString("") { it.text }
        if (text.isEmpty()) {
            return 0f
        }

        val widthPx = unitConverter.ptToPx(widthPt).toInt()
        val paint = createTextPaint(spans[0])  // Use first span as base
        
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(alignmentToLayout(style.alignment))
            .setLineSpacing(
                unitConverter.ptToPx(style.spacing.line?.toFloat() ?: 0f),
                style.spacing.line?.toFloat()?.let { unitConverter.ptToPx(it) / unitConverter.ptToPx(paint.textSize) } ?: 1f
            )
            .setIncludePad(true)
            .build()

        val heightPx = layout.height.toFloat()
        return unitConverter.pxToPt(heightPx)
    }

    /**
     * Measure the height of plain text (single style) in Points.
     */
    fun measurePlainText(
        text: String,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        fontFamily: String? = null,
        widthPt: Float
    ): Float {
        if (text.isEmpty()) {
            return 0f
        }

        val widthPx = unitConverter.ptToPx(widthPt).toInt()
        val paint = TextPaint().apply {
            textSize = unitConverter.ptToPx(fontSize)
            typeface = getTypeface(fontFamily, isBold, isItalic)
            isAntiAlias = true
        }

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setIncludePad(true)
            .build()

        val heightPx = layout.height.toFloat()
        return unitConverter.pxToPt(heightPx)
    }

    /**
     * Get the exact line that fits within a given height.
     * Used for splitting paragraphs across pages.
     *
     * @param spans The text spans
     * @param style The paragraph style
     * @param widthPt The available width
     * @param maxHeightPt The maximum available height
     * @return The line number (0-based) that is the last one to fit, or -1 if nothing fits
     */
    fun getLastFittingLine(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        widthPt: Float,
        maxHeightPt: Float
    ): Int {
        if (spans.isEmpty()) return -1

        val text = spans.joinToString("") { it.text }
        if (text.isEmpty()) return -1

        val widthPx = unitConverter.ptToPx(widthPt).toInt()
        val maxHeightPx = unitConverter.ptToPx(maxHeightPt).toInt()
        val paint = createTextPaint(spans[0])

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(alignmentToLayout(style.alignment))
            .setLineSpacing(
                unitConverter.ptToPx(style.spacing.line?.toFloat() ?: 0f),
                1f
            )
            .setIncludePad(true)
            .build()

        // Use binary search to find the last line that fits
        return layout.getLineForVertical(maxHeightPx)
    }

    /**
     * Get the character offset (in the original text) for a specific line.
     * Used when splitting text fragments.
     */
    fun getLineStart(layout: StaticLayout, lineNumber: Int): Int {
        return if (lineNumber < layout.lineCount) {
            layout.getLineStart(lineNumber)
        } else {
            layout.text.length
        }
    }

    /**
     * Get the character offset for the end of a specific line.
     */
    fun getLineEnd(layout: StaticLayout, lineNumber: Int): Int {
        return if (lineNumber < layout.lineCount) {
            layout.getLineEnd(lineNumber)
        } else {
            layout.text.length
        }
    }

    /**
     * Create or retrieve a cached TextPaint for a given TextSpan.
     * Reusing TextPaint objects is more efficient than creating new ones.
     */
    private fun createTextPaint(span: TextSpan): TextPaint {
        val cacheKey = "${span.fontFamily}_${span.fontSize}_${span.isBold}_${span.isItalic}"
        
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

    /**
     * Get or create a Typeface for the given font attributes.
     */
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

    /**
     * Parse a hex color string (e.g., "FF0000") to an ARGB integer.
     */
    private fun parseColorString(colorStr: String): Int {
        return try {
            val hex = if (colorStr.startsWith("#")) colorStr.substring(1) else colorStr
            // Pad with FF (opaque alpha) if only 6 digits
            val fullHex = if (hex.length == 6) "FF$hex" else hex
            fullHex.toLong(16).toInt()
        } catch (e: Exception) {
            0xFF000000.toInt() // Default to black
        }
    }

    /**
     * Convert ParagraphAlignment to Android's Layout.Alignment.
     */
    private fun alignmentToLayout(alignment: com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment): Layout.Alignment {
        return when (alignment) {
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.JUSTIFIED -> Layout.Alignment.ALIGN_NORMAL
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.DISTRIBUTED -> Layout.Alignment.ALIGN_CENTER
        }
    }

    /**
     * Clear the paint cache to free memory.
     * Call this when rebuilding the pagination after major edits.
     */
    fun clearCache() {
        textPaintCache.clear()
    }
}
