package com.tosin.docprocessor.data.common.model.layout

/**
 * Defines the boundary for the layout engine using Points (pt).
 * 1 inch = 72 points.
 */
data class PageDimensions(
    val width: Float = 612f,   // Standard Letter (8.5" x 72)
    val height: Float = 792f,  // Standard Letter (11" x 72)
    val marginLeft: Float = 72f,
    val marginTop: Float = 72f,
    val marginRight: Float = 72f,
    val marginBottom: Float = 72f
) {
    val printableWidth: Float get() = width - marginLeft - marginRight
    val printableHeight: Float get() = height - marginTop - marginBottom

    companion object {
        val Letter = PageDimensions(width = 612f, height = 792f)
        val A4 = PageDimensions(width = 595.27f, height = 841.89f)
    }
}
