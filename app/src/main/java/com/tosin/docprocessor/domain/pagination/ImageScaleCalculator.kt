package com.tosin.docprocessor.domain.pagination

/**
 * Unit-agnostic result of scaling an image: all four lengths share the
 * caller-chosen unit (points from the paginator, pixels from the composable).
 */
data class ImageScaleResult(
    val displayWidth: Float,
    val displayHeight: Float,
    val intrinsicWidth: Float?,
    val intrinsicHeight: Float?,
    val isFallback: Boolean
)

/**
 * Pure, unit-agnostic image scaling math shared by the print paginator and the
 * Compose widget so both surfaces agree on a single layout rule.
 *
 * Rules:
 *  - Never upscale: images smaller than the available width keep their natural size.
 *  - Never distort: the aspect ratio is always preserved.
 *  - Never overflow: a height cap scales tall images down to fit.
 */
object ImageScaleCalculator {

    fun scale(
        intrinsicWidth: Float?,
        intrinsicHeight: Float?,
        availableWidth: Float,
        maxHeight: Float,
        fallbackHeight: Float = ImageLayoutConfig.Default.fallbackHeightPt
    ): ImageScaleResult {
        val safeAvailable = availableWidth.coerceAtLeast(0f)
        if (safeAvailable <= 0f) {
            return ImageScaleResult(0f, 0f, intrinsicWidth, intrinsicHeight, isFallback = true)
        }

        val iw = intrinsicWidth?.takeIf { it > 0f }
        val ih = intrinsicHeight?.takeIf { it > 0f }
        if (iw == null || ih == null) {
            return ImageScaleResult(
                displayWidth = safeAvailable,
                displayHeight = fallbackHeight.coerceAtLeast(1f),
                intrinsicWidth = iw,
                intrinsicHeight = ih,
                isFallback = true
            )
        }

        // Never upscale (1f); fit horizontally; cap by the maximum height.
        val scaleFactor = minOf(
            1f,
            safeAvailable / iw,
            if (maxHeight > 0f) maxHeight / ih else Float.POSITIVE_INFINITY
        )

        return ImageScaleResult(
            displayWidth = iw * scaleFactor,
            displayHeight = ih * scaleFactor,
            intrinsicWidth = iw,
            intrinsicHeight = ih,
            isFallback = false
        )
    }
}