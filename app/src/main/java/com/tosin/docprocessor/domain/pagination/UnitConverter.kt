package com.tosin.docprocessor.domain.pagination

import android.content.Context
import android.util.TypedValue
import kotlin.math.round

/**
 * Strict unit converter for Points <-> Pixels and Points <-> Inches.
 * Prevents floating-point rounding errors that cause text to "jitter."
 *
 * All measurements external to the engine are in Points (PT).
 * All measurements internal to Android are in Pixels (PX).
 *
 * Standards:
 * - 1 inch = 72 points (industry standard for documents)
 * - 1 inch = displayDensity * 160 dpi (Android standard)
 *
 * This means: 1 PT = displayDensity * (160/72) PX ≈ displayDensity * 2.222 PX
 */
class UnitConverter(context: Context) {
    private val displayMetrics = context.resources.displayMetrics
    
    /**
     * Conversion factor: PX per PT
     * displayDensity is dimensionless (1.0 for 160dpi, 2.0 for 320dpi, etc.)
     */
    private val pxPerPt: Float = displayMetrics.density * (160f / 72f)
    
    /**
     * Conversion factor: PT per PX
     */
    private val ptPerPx: Float = 1f / pxPerPt

    /**
     * Convert Points to Pixels with rounding for consistent layout.
     * Rounds to nearest 0.5 pixel to prevent sub-pixel jitter.
     */
    fun ptToPx(points: Float): Float {
        val px = points * pxPerPt
        // Round to nearest 0.5 pixel to avoid sub-pixel rendering issues
        return round(px * 2f) / 2f
    }

    /**
     * Convert Pixels to Points.
     * Rounds to nearest 0.01 point for precision.
     */
    fun pxToPt(pixels: Float): Float {
        val pt = pixels * ptPerPx
        // Round to 0.01 point precision
        return round(pt * 100f) / 100f
    }

    /**
     * Convert Inches to Points.
     */
    fun inchesToPt(inches: Float): Float {
        return inches * 72f
    }

    /**
     * Convert Points to Inches.
     */
    fun ptToInches(points: Float): Float {
        return points / 72f
    }

    /**
     * Convert Inches to Pixels.
     */
    fun inchesToPx(inches: Float): Float {
        val px = inches * displayMetrics.ydpi
        return round(px * 2f) / 2f
    }

    /**
     * Convert Pixels to Inches.
     */
    fun pxToInches(pixels: Float): Float {
        return pixels / displayMetrics.ydpi
    }

    /**
     * Batch conversion helper: convert a list of Points to Pixels.
     */
    fun ptToPxBatch(pointsList: List<Float>): List<Float> {
        return pointsList.map { ptToPx(it) }
    }

    /**
     * Get the scaling factor for visual consistency.
     * Useful for scaling UI elements proportionally.
     */
    fun getScaleFactor(): Float = pxPerPt

    /**
     * Get display density (1.0 for 160dpi, 2.0 for 320dpi).
     */
    fun getDensity(): Float = displayMetrics.density

    /**
     * Verify precision: ensure round-trip conversion doesn't lose data.
     * Example: 100 PT -> PX -> PT should equal 100 PT (within tolerance).
     */
    fun verifyRoundTripConversion(originalPt: Float): Boolean {
        val px = ptToPx(originalPt)
        val backToPt = pxToPt(px)
        val tolerance = 0.01f // 0.01 point tolerance
        return kotlin.math.abs(originalPt - backToPt) < tolerance
    }
}
