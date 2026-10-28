// path/to/File.kt
package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.runtime.Immutable
import kotlin.math.min

/**
 * Single scale owner for consistent scaling across canvas and overlay
 * P0-6: Consistent scaling
 */
@Immutable
data class PageScaler(
    val baseWidthPx: Float,
    val baseHeightPx: Float,
    val fitWidthPx: Float,
    val fitHeightPx: Float,
    val userScale: Float = 1f
) {
    val fitScale: Float
        get() = min(fitWidthPx / baseWidthPx, fitHeightPx / baseHeightPx)
    
    val effectiveScale: Float
        get() = fitScale * userScale
    
    fun scalePx(px: Float): Float = px * effectiveScale
}
