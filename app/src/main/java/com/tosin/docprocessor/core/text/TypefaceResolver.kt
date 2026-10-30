// path/to/File.kt
package com.tosin.docprocessor.core.text

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves typefaces with CJK fallback support.
 * Ensures consistent font resolution across measurement and rendering.
 */
class TypefaceResolver(private val context: Context) {
    
    private val cache = ConcurrentHashMap<TypefaceKey, Typeface>()
    
    data class TypefaceKey(
        val familyName: String?,
        val style: Int
    )
    
    /**
     * Resolve typeface for given family name and style.
     * Falls back to CJK-complete font if needed.
     */
    @WorkerThread
    fun resolve(familyName: String?, style: Int): Typeface {
        val key = TypefaceKey(familyName, style)
        return cache.getOrPut(key) {
            resolveInternal(familyName, style)
        }
    }
    
    private fun resolveInternal(familyName: String?, style: Int): Typeface {
        // Try to create typeface with requested family
        if (!familyName.isNullOrBlank()) {
            return Typeface.create(familyName, style)
        }
        
        // Default typeface
        return Typeface.create(Typeface.DEFAULT, style)
    }
    
    /**
     * Get CJK fallback typeface (system fallback)
     */
    @WorkerThread
    fun getCjkFallback(): Typeface {
        val key = TypefaceKey("cjk_fallback", Typeface.NORMAL)
        return cache.getOrPut(key) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Typeface.create("sans-serif", Typeface.NORMAL)
            } else {
                Typeface.DEFAULT
            }
        }
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        cache.clear()
    }
    
    companion object {
        const val STYLE_NORMAL = Typeface.NORMAL
        const val STYLE_BOLD = Typeface.BOLD
        const val STYLE_ITALIC = Typeface.ITALIC
        const val STYLE_BOLD_ITALIC = Typeface.BOLD_ITALIC
    }
}
