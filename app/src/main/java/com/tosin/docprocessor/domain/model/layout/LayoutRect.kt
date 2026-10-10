package com.tosin.docprocessor.domain.model.layout

/**
 * A pure-domain rectangle expressed in Points (pt).
 *
 * Unlike [android.graphics.RectF] this type has no Android dependency, which keeps
 * the pagination/layout logic unit-testable on the JVM.
 */
data class LayoutRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}
