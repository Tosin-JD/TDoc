package com.tosin.docprocessor.domain.model.layout

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
    val marginBottom: Float = 72f,
    val headerHeight: Float = 0f,
    val footerHeight: Float = 0f
) {
    val printableWidth: Float get() = width - marginLeft - marginRight
    val printableHeight: Float get() = height - marginTop - marginBottom

    /** Top of body content (below margin + header reservation) */
    val bodyTop: Float get() = marginTop + headerHeight

    /** Bottom of body content (above margin + footer reservation) */
    val bodyBottom: Float get() = height - marginBottom - footerHeight

    /** Usable body height after header/footer reservation */
    val bodyHeight: Float get() = bodyBottom - bodyTop

    /** The rectangle for header content */
    val headerRect: LayoutRect
        get() = LayoutRect(
            left = marginLeft,
            top = marginTop,
            right = marginLeft + printableWidth,
            bottom = marginTop + headerHeight
        )

    /** The rectangle for footer content */
    val footerRect: LayoutRect
        get() = LayoutRect(
            left = marginLeft,
            top = height - marginBottom - footerHeight,
            right = marginLeft + printableWidth,
            bottom = height - marginBottom
        )

    /** The rectangle for body content */
    val bodyRect: LayoutRect
        get() = LayoutRect(
            left = marginLeft,
            top = bodyTop,
            right = marginLeft + printableWidth,
            bottom = bodyBottom
        )

    companion object {
        val Letter = PageDimensions(width = 612f, height = 792f)
        val A4 = PageDimensions(width = 595.27f, height = 841.89f)
    }
}
