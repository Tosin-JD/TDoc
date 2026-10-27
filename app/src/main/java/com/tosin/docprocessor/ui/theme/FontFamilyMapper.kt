package com.tosin.docprocessor.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.tosin.docprocessor.domain.typography.FontFamilyKey

/**
 * Maps a platform-free [FontFamilyKey] to a Compose [FontFamily].
 *
 * System families (`SansSerif`, `Serif`, `Monospace`, `Cursive`) are stable and
 * still participate in Android's per-glyph fallback (CJK, emoji, complex
 * scripts), so no bundled fonts are required.
 */
fun FontFamilyKey.toComposeFontFamily(): FontFamily = when (this) {
    FontFamilyKey.SERIF -> FontFamily.Serif
    FontFamilyKey.MONOSPACE -> FontFamily.Monospace
    FontFamilyKey.CURSIVE -> FontFamily.Cursive
    FontFamilyKey.SANS_SERIF, FontFamilyKey.DEFAULT, FontFamilyKey.CARLITO, FontFamilyKey.LIBERATION_SANS -> FontFamily.SansSerif
    FontFamilyKey.LIBERATION_SERIF -> FontFamily.Serif
    FontFamilyKey.DEJAVU_MONO -> FontFamily.Monospace
}
