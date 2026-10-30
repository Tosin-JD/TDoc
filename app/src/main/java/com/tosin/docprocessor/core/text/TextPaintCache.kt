// path/to/File.kt
package com.tosin.docprocessor.core.text

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache key for TextPaint instances
 */
data class TextPaintKey(
    val typefaceName: String?,
    val sizePx: Float,
    val style: Int,
    val letterSpacing: Float,
    val skewX: Float,
    val flags: Int
)

/**
 * Caches TextPaint instances for efficient reuse
 */
class TextPaintCache(private val typefaceResolver: TypefaceResolver) {
    
    private val cache = ConcurrentHashMap<TextPaintKey, TextPaint>()
    
    @WorkerThread
    fun getPaint(key: TextPaintKey): TextPaint {
        return cache.getOrPut(key) {
            createPaint(key)
        }
    }
    
    private fun createPaint(key: TextPaintKey): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = key.sizePx
            typeface = typefaceResolver.resolve(key.typefaceName, key.style)
            letterSpacing = key.letterSpacing
            textSkewX = key.skewX
            flags = key.flags
        }
    }
    
    fun clear() {
        cache.clear()
    }
}
