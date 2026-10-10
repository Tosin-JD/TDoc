package com.tosin.docprocessor.domain.pagination

/**
 * Tuning knobs for image layout, shared by the print paginator and the Compose
 * widget so both surfaces scale identically.
 */
data class ImageLayoutConfig(
    /** Height used when intrinsic dimensions cannot be resolved. */
    val fallbackHeightPt: Float = 150f,
    /** Gap between the image and its caption, in display points. */
    val captionSpacingPt: Float = 4f,
    /** Fraction of the usable area an image may occupy before sizing vertically caps. */
    val maxHeightFraction: Float = 0.8f
) {
    companion object {
        val Default = ImageLayoutConfig()
    }
}