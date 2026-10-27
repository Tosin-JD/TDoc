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
    val footerHeight: Float = 0f,
    /** Distance from the top edge of the sheet to the top of header content (Word: `w:pgMar w:header`). */
    val headerEdgePt: Float = 36f,
    /** Distance from the bottom edge of the sheet to the bottom of footer content (Word: `w:pgMar w:footer`). */
    val footerEdgePt: Float = 36f
) {
    val printableWidth: Float get() = width - marginLeft - marginRight
    val printableHeight: Float get() = height - marginTop - marginBottom

    /** Top of body content (below margin + header reservation) */
    val bodyTop: Float get() = marginTop + headerHeight

    /** Bottom of body content (above margin + footer reservation) */
    val bodyBottom: Float get() = height - marginBottom - footerHeight

    /** Usable body height after header/footer reservation */
    val bodyHeight: Float get() = bodyBottom - bodyTop

    /** The rectangle for header content — sits between sheet edge and margin top */
    val headerRect: LayoutRect
        get() = LayoutRect(
            left = marginLeft,
            top = headerEdgePt,
            right = marginLeft + printableWidth,
            bottom = marginTop
        )

    /** The rectangle for footer content — sits between margin bottom and sheet edge */
    val footerRect: LayoutRect
        get() = LayoutRect(
            left = marginLeft,
            top = height - marginBottom,
            right = marginLeft + printableWidth,
            bottom = height - footerEdgePt
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
        val Legal = PageDimensions(width = 612f, height = 1008f)
        val Executive = PageDimensions(width = 540f, height = 720f)
    }
}

