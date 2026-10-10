package com.tosin.docprocessor.domain.model.layout

import android.text.StaticLayout

/**
 * Typed payload produced by the layout engine for a [PositionedElement].
 *
 * Replaces the previous untyped `layoutResult: Any?` so renderers no longer need
 * to perform blind casts (and so table/text/image layouts can be exhausted).
 */
sealed class LayoutResult {
    data class TextLayout(val layout: StaticLayout) : LayoutResult()
    data class TableLayout(val layout: TableRenderLayout) : LayoutResult()
    data class ImageLayout(
        val intrinsicWidthPt: Float?,
        val intrinsicHeightPt: Float?,
        val displayWidthPt: Float,
        val displayHeightPt: Float,
        val sourceUri: String,
        val caption: String? = null,
        val captionLayout: StaticLayout? = null
    ) : LayoutResult()

    object Empty : LayoutResult()
}
