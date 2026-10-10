package com.tosin.docprocessor.core.text

import android.graphics.Typeface
import com.tosin.docprocessor.domain.typography.FontFamilyKey

/**
 * Maps a [FontFamilyKey] and style flags to an `android.graphics.Typeface`,
 * caching the result. Document font names never reach `Typeface.create`
 * directly, so unknown names resolve deterministically to a generic family and
 * the platform fallback chain handles missing glyphs.
 */
object AndroidTypefaceMapper {

    private val cache = HashMap<Pair<FontFamilyKey, Int>, Typeface>()

    fun map(key: FontFamilyKey, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return cache.getOrPut(key to style) {
            Typeface.create(familyName(key), style)
        }
    }

    fun clearCache() {
        cache.clear()
    }

    private fun familyName(key: FontFamilyKey): String = when (key) {
        FontFamilyKey.SERIF -> "serif"
        FontFamilyKey.MONOSPACE -> "monospace"
        FontFamilyKey.SANS_SERIF, FontFamilyKey.DEFAULT -> "sans-serif"
        FontFamilyKey.CURSIVE -> "cursive"
    }
}
