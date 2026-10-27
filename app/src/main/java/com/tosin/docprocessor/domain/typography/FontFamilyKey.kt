package com.tosin.docprocessor.domain.typography

/**
  * Coarse bucket a document font maps to. Platform adapters translate a bucket
 * into a Compose [androidx.compose.ui.text.font.FontFamily] or an
 * [android.graphics.Typeface]. Keeping the bucket platform-free lets both
 * render paths share one mapping and keeps the logic JVM-testable.
 *
 * Keys prefixed with BUNDLED_ resolve to fonts included in `res/font/` that
 * are metric-compatible with common Microsoft Office fonts.
 */
enum class FontFamilyKey {
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    CURSIVE,
    DEFAULT,
    /** Metric-compatible replacement for Calibri (bundled as carlito_*.ttf). */
    CARLITO,
    /** Metric-compatible replacement for Arial (bundled as liberation_sans_*.ttf). */
    LIBERATION_SANS,
    /** Metric-compatible replacement for Times New Roman (bundled as liberation_serif_*.ttf). */
    LIBERATION_SERIF,
    /** Metric-compatible replacement for Courier New / Consolas (bundled as dejavu_mono_*.ttf). */
    DEJAVU_MONO
}

