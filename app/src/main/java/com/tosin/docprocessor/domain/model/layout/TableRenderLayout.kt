package com.tosin.docprocessor.domain.model.layout

import android.text.StaticLayout
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign

data class TableRenderLayout(
    val columnCount: Int,
    val rowLayouts: List<TableRowLayout>,
    val cellPaddingPt: Float,
    /** Resolved column widths in points (from grid or heuristic). */
    val gridColumnWidthsPt: List<Float> = emptyList(),
    /** Total table width in points the columns were laid out for. */
    val widthPt: Float = 0f
) {
    val totalHeightPt: Float
        get() = rowLayouts.sumOf { it.heightPt.toDouble() }.toFloat()
}

data class TableRowLayout(
    val heightPt: Float,
    val cells: List<TableCellLayout>,
    val isHeader: Boolean
)

data class TableCellLayout(
    val text: String,
    val widthPt: Float,
    val contentHeightPt: Float,
    val layout: StaticLayout?,
    /** True when this cell is an hMerge/vMerge continuation — skip rendering. */
    val isCovered: Boolean = false,
    /** Number of columns this cell spans (gridSpan). */
    val colSpan: Int = 1,
    /** Number of rows this cell spans (vMerge). */
    val rowSpan: Int = 1,
    /** Cell background color (hex without #). */
    val backgroundColor: String? = null,
    /** Explicit cell borders. */
    val borders: CellBorders? = null,
    /**
     * Shared-edge ownership-filtered borders from the resolved presentation.
     * When non-null the renderer paints exactly these edges (no table-level
     * fallback); null means the legacy `borders`/table fallback applies.
     */
    val paintBorders: CellBorders? = null,
    /** Explicit cell padding/margins in points. */
    val margins: CellBorders? = null,
    /** Vertical alignment within cell. */
    val verticalAlignment: VerticalCellAlign = VerticalCellAlign.TOP
)
