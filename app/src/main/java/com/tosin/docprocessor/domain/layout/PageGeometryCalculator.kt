package com.tosin.docprocessor.domain.layout

import com.tosin.docprocessor.domain.model.layout.PageDimensions

/**
 * Responsible for calculating precise geometry for document layout and rendering.
 * Addresses the issues identified in TODO8.md by centralizing geometry calculations
 * and ensuring consistent application of scales and margins.
 */
class PageGeometryCalculator {

    /**
     * Calculates the effective dimensions to use for layout, applying scale factor
     * to the entire page surface.
     */
    fun calculateEffectiveDimensions(
        baseDimensions: PageDimensions,
        scaleFactor: Float
    ): PageDimensions {
        return baseDimensions.copy(
            width = baseDimensions.width * scaleFactor,
            height = baseDimensions.height * scaleFactor,
            marginLeft = baseDimensions.marginLeft * scaleFactor,
            marginTop = baseDimensions.marginTop * scaleFactor,
            marginRight = baseDimensions.marginRight * scaleFactor,
            marginBottom = baseDimensions.marginBottom * scaleFactor,
            headerHeight = baseDimensions.headerHeight * scaleFactor,
            footerHeight = baseDimensions.footerHeight * scaleFactor,
            headerEdgePt = baseDimensions.headerEdgePt * scaleFactor,
            footerEdgePt = baseDimensions.footerEdgePt * scaleFactor
        )
    }

    /**
     * Scales an individual dimension (e.g., spacing, indentation) by the provided scale factor.
     */
    fun scaleDimension(dimension: Float, scaleFactor: Float): Float = dimension * scaleFactor
}
