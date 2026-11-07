package com.tosin.docprocessor.data.parser.internal.models

import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign

/**
 * A table cell carrying styled text [spans] (preserving font family, size,
 * bold, italic, color, etc.) and structural metadata (colspan, vMerge,
 * shading, borders, padding and alignment). Used by the rich rendering paths
 * in both PrintPaginator / PrintElementRenderer and the mobile Compose table.
 */
data class RichTableCell(
    val spans: List<TextSpan>,
    val metadata: TableCellMetadata = TableCellMetadata(),
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    /** True when this grid position is covered by an incoming hMerge or vMerge. */
    val isCovered: Boolean = false,
    /** Hex colour without the leading `#`, e.g. `D9E2F3`. */
    val backgroundColor: String? = null,
    val borders: CellBorders? = null,
    /** Cell padding/margins in points, resolved from `w:tcMar`. */
    val margins: CellBorders? = null,
    val verticalAlignment: VerticalCellAlign = VerticalCellAlign.TOP,
    val horizontalAlignment: TextAlignment = TextAlignment.START,
    val widthPoints: Float? = null
)
