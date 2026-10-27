package com.tosin.docprocessor.data.parser.internal.models

/**
 * A table cell carrying styled text [spans] (preserving font family, size,
 * bold, italic, color, etc.) and structural [metadata] (colspan, vMerge,
 * shading). Used by the rich rendering path in PrintPaginator / PrintElementRenderer.
 */
data class RichTableCell(
    val spans: List<TextSpan>,
    val metadata: TableCellMetadata = TableCellMetadata()
)
