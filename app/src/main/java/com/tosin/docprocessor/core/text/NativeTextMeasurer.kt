// path/to/File.kt
package com.tosin.docprocessor.core.text

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.WorkerThread
import com.tosin.docprocessor.domain.pagination.UnitConverter
import java.util.concurrent.ConcurrentHashMap

/**
 * Native Paint-based text measurer for high performance
 */
class NativeTextMeasurer(
    private val context: Context,
    private val unitConverter: UnitConverter,
    private val typefaceResolver: TypefaceResolver = TypefaceResolver(context)
) {
    
    private val paintCache = TextPaintCache(typefaceResolver)
    private val layoutCache = ConcurrentHashMap<LayoutKey, StaticLayout>()
    
    data class LayoutKey(
        val textHash: Int,
        val paintKey: TextPaintKey,
        val widthPx: Int,
        val alignment: Layout.Alignment,
        val lineSpacingPx: Float = 0f,
        val lineSpacingMultiplier: Float = 1f,
        val justificationMode: Int = Layout.JUSTIFICATION_MODE_NONE,
        val firstLineIndentPx: Int = 0,
        val restLineIndentPx: Int = 0
    )
    
    @WorkerThread
    fun measureWidth(text: String, paintKey: TextPaintKey): Float {
        val paint = paintCache.getPaint(paintKey)
        return paint.measureText(text)
    }
    
    @WorkerThread
    fun measureText(text: String, paintKey: TextPaintKey): Float {
        val paint = paintCache.getPaint(paintKey)
        return paint.measureText(text)
    }
    
    @WorkerThread
    fun getTextBounds(text: String, paintKey: TextPaintKey): android.graphics.Rect {
        val paint = paintCache.getPaint(paintKey)
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        return bounds
    }
    
    @WorkerThread
    fun buildStaticLayout(
        text: CharSequence,
        paintKey: TextPaintKey,
        widthPt: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineSpacingPx: Float = 0f,
        lineSpacingMultiplier: Float = 1f,
        justificationMode: Int = Layout.JUSTIFICATION_MODE_NONE,
        firstLineIndentPx: Int = 0,
        restLineIndentPx: Int = 0
    ): StaticLayout {
        val paint = paintCache.getPaint(paintKey)
        val widthPx = unitConverter.ptToPx(widthPt).toInt().coerceAtLeast(1)
        val key = LayoutKey(
            textHash = text.hashCode(),
            paintKey = paintKey,
            widthPx = widthPx,
            alignment = alignment,
            lineSpacingPx = lineSpacingPx,
            lineSpacingMultiplier = lineSpacingMultiplier,
            justificationMode = justificationMode,
            firstLineIndentPx = firstLineIndentPx,
            restLineIndentPx = restLineIndentPx
        )
        
        return layoutCache.getOrPut(key) {
            @Suppress("DEPRECATION")
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                .setAlignment(alignment)
                .setLineSpacing(lineSpacingPx, lineSpacingMultiplier)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setIncludePad(false)

            if (justificationMode != Layout.JUSTIFICATION_MODE_NONE) {
                builder.setJustificationMode(justificationMode)
            }

            if (firstLineIndentPx != 0 || restLineIndentPx != 0) {
                val indents = IntArray(text.length + 1)
                if (text.isNotEmpty()) {
                    indents[0] = firstLineIndentPx.coerceAtLeast(0)
                }
                for (i in 1..text.length) {
                    indents[i] = restLineIndentPx.coerceAtLeast(0)
                }
                builder.setIndents(indents, null)
            }

            builder.build()
        }
    }
    
    fun clearCache() {
        paintCache.clear()
        layoutCache.clear()
    }
}
