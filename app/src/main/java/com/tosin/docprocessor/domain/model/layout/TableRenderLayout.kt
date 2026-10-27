package com.tosin.docprocessor.domain.model.layout

import android.text.StaticLayout

data class TableRenderLayout(
    val columnCount: Int,
    val rowLayouts: List<TableRowLayout>,
    val cellPaddingPt: Float,
    /** Resolved column widths in points (from grid or heuristic). */
    val gridColumnWidthsPt: List<Float> = emptyList()
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
    /** True when this cell is a vMerge/hMerge continuation — skip rendering. */
    val isCovered: Boolean = false,
    /** Number of columns this cell spans (gridSpan). */
    val colSpan: Int = 1,
    /** Number of rows this cell spans (vMerge). */
    val rowSpan: Int = 1
)

