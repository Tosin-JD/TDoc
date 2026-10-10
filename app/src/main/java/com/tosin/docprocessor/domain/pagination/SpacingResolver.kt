package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing

/**
 * Resolved paragraph spacing values in Points (pt).
 */
data class ResolvedSpacing(
    val beforePt: Float = 0f,
    val afterPt: Float = 0f,
    val lineSpacingPt: Float = 0f
)

/**
 * Converts [ParagraphSpacing] (twips) into points.
 */
class SpacingResolver {
    private fun twipsToPt(twips: Int): Float = twips * (72f / 1440f)

    fun resolve(spacing: ParagraphSpacing): ResolvedSpacing {
        return ResolvedSpacing(
            beforePt = spacing.before?.let { twipsToPt(it) } ?: 0f,
            afterPt = spacing.after?.let { twipsToPt(it) } ?: 0f,
            lineSpacingPt = spacing.line?.let { twipsToPt(it) } ?: 0f
        )
    }
}
