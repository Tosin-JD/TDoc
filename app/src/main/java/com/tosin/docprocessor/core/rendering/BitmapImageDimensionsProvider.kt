package com.tosin.docprocessor.core.rendering

import android.graphics.BitmapFactory
import com.tosin.docprocessor.domain.pagination.ImageDimensions
import com.tosin.docprocessor.domain.pagination.ImageDimensionsProvider
import java.io.File

/**
 * Decodes image *bounds* only (inJustDecodeBounds) and converts pixels to points.
 *
 * A document pixel is treated as 0.75 pt (1 px @ 96 dpi = 72/96 pt), which is the
 * same 0.75 factor Units.parseOdfLength("px") applies — one consistent convention.
 */
class BitmapImageDimensionsProvider : ImageDimensionsProvider {

    override fun intrinsicPoints(sourceUri: String): ImageDimensions? {
        val file = File(sourceUri)
        if (!file.exists() || !file.isFile) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceUri, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        return ImageDimensions(
            widthPt = options.outWidth * PX_TO_PT,
            heightPt = options.outHeight * PX_TO_PT
        )
    }

    private companion object {
        const val PX_TO_PT = 0.75f
    }
}