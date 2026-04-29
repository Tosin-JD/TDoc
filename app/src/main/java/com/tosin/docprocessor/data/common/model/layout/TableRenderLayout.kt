package com.tosin.docprocessor.data.common.model.layout

import android.text.StaticLayout

data class TableRenderLayout(
    val columnCount: Int,
    val rowLayouts: List<TableRowLayout>,
    val cellPaddingPt: Float
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
    val layout: StaticLayout?
)
