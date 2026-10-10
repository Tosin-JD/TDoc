package com.tosin.docprocessor.domain.typography

/**
 * Coarse bucket a document font maps to. Platform adapters translate a bucket
 * into a Compose [androidx.compose.ui.text.font.FontFamily] or an
 * [android.graphics.Typeface]. Keeping the bucket platform-free lets both
 * render paths share one mapping and keeps the logic JVM-testable.
 */
enum class FontFamilyKey {
    SANS_SERIF,
    SERIF,
    MONOSPACE,
    CURSIVE,
    DEFAULT
}
