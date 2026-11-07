package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.domain.model.layout.PageDimensions

/**
 * Converts [SectionProperties] (parsed from DOCX XML, values in twips) into a
 * layout-ready [PageDimensions] (values in points).
 *
 * 1 inch = 1440 twips = 72 points.
 */
class SectionResolver {
    /** 1 twip = 1/1440 inch = 72/1440 points = 0.05 points */
    private fun twipsToPt(twips: Int): Float = twips * (72f / 1440f)

    fun resolve(section: SectionProperties): PageDimensions {
        val rawWidth = section.pageWidth?.takeIf { it > 0 }?.let { twipsToPt(it) }
            ?: PageDimensions.Default.width
        val rawHeight = section.pageHeight?.takeIf { it > 0 }?.let { twipsToPt(it) }
            ?: PageDimensions.Default.height
        val landscape = section.orientation.equals("landscape", ignoreCase = true)
        val portrait = section.orientation.equals("portrait", ignoreCase = true)
        val (width, height) = when {
            landscape && rawWidth < rawHeight -> rawHeight to rawWidth
            portrait && rawWidth > rawHeight -> rawHeight to rawWidth
            else -> rawWidth to rawHeight
        }
        return PageDimensions(
            width = width,
            height = height,
            marginLeft = (section.margins.left?.let { twipsToPt(it) } ?: 72f) + (section.gutter?.let { twipsToPt(it) } ?: 0f),
            marginTop = section.margins.top?.let { twipsToPt(it) } ?: 72f,
            marginRight = section.margins.right?.let { twipsToPt(it) } ?: 72f,
            marginBottom = section.margins.bottom?.let { twipsToPt(it) } ?: 72f,
            headerEdgePt = section.headerEdge?.let { twipsToPt(it) } ?: 36f,
            footerEdgePt = section.footerEdge?.let { twipsToPt(it) } ?: 36f,
            pageBackground = section.pageBackground
        )
    }
}
