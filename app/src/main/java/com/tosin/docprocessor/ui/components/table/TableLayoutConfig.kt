package com.tosin.docprocessor.ui.components.table

/**
 * Visual and measurement configuration for document tables. Kept separate from
 * the layout math so callers (and future table-editing features) can tune the
 * presentation without touching the sizing algorithm.
 */
data class TableLayoutConfig(
    /** Minimum width a column may shrink to before horizontal scrolling begins. */
    val minColumnWidthDp: Float = 120f,
    /** Soft cap so a single column does not dominate an ultra-wide screen. */
    val maxColumnWidthDp: Float = 420f,
    val cellPaddingHorizontalDp: Float = 12f,
    val cellPaddingVerticalDp: Float = 10f,
    val borderWidthDp: Float = 1f,
    val cornerRadiusDp: Float = 12f,
    val zebraStriping: Boolean = true
) {
    companion object {
        val Default = TableLayoutConfig()
    }
}
