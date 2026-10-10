package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.domain.model.layout.ElementIndentation

/**
 * Converts [ParagraphIndentation] (twips) plus an optional [ListInfo] nesting
 * level into resolved [ElementIndentation] values in points.
 */
class IndentationResolver {
    private fun twipsToPt(twips: Int): Float = twips * (72f / 1440f)

    /** Default list indent per level in points (0.5 inch per level). */
    private val listIndentPerLevelPt = 36f

    fun resolve(
        indentation: ParagraphIndentation,
        listInfo: ListInfo? = null
    ): ElementIndentation {
        val listIndentPt = listInfo?.let { it.level.coerceAtLeast(0) * listIndentPerLevelPt } ?: 0f

        return ElementIndentation(
            leftPt = (indentation.left?.let { twipsToPt(it) } ?: 0f) + listIndentPt,
            rightPt = indentation.right?.let { twipsToPt(it) } ?: 0f,
            firstLinePt = indentation.firstLine?.let { twipsToPt(it) } ?: 0f,
            hangingPt = indentation.hanging?.let { twipsToPt(it) } ?: 0f
        )
    }
}
