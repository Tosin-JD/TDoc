package com.tosin.docprocessor.domain.pagination

/**
 * Intrinsic size of an image resource in Points (pt).
 */
data class ImageDimensions(
    val widthPt: Float,
    val heightPt: Float
)

/**
 * Resolves the intrinsic size of an image resource in Points (pt).
 *
 * Implementations decode image *bounds* (e.g. BitmapFactory inJustDecodeBounds),
 * never the full pixel buffer, so measurement stays cheap even for large media.
 */
interface ImageDimensionsProvider {
    /** Returns null when the file is missing or the format is not decodable. */
    fun intrinsicPoints(sourceUri: String): ImageDimensions?
}