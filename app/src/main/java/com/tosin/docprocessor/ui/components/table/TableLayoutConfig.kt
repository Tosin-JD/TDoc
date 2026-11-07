package com.tosin.docprocessor.ui.components.table

/**
 * Measurement configuration for document tables. These are layout
 * affordances only (scroll thresholds, readability minimums) — never visual
 * styling. Document-authored paint comes exclusively from the resolved
 * table presentation.
 */
data class TableLayoutConfig(
    /** Minimum width a column may shrink to before horizontal scrolling begins. */
    val minColumnWidthDp: Float = 120f,
    /** Soft cap so a single column does not dominate an ultra-wide screen. */
    val maxColumnWidthDp: Float = 420f
) {
    companion object {
        val Default = TableLayoutConfig()
    }
}
