package com.tosin.docprocessor.ui.components.table

data class TableLayoutResult(
    val columnWidthsDp: List<Float>,
    val totalWidthDp: Float,
    val requiresScroll: Boolean
)

/**
 * Pure, Compose-free column width solver.
 *
 * Behaviour:
 * - When the viewport is wide enough, columns are distributed proportionally to
 *   their content weights and the table fills the available width.
 * - When the viewport is too narrow, every column bottoms out at
 *   [TableLayoutConfig.minColumnWidthDp]; the total then exceeds the viewport
 *   and [TableLayoutResult.requiresScroll] becomes true so the UI can scroll.
 */
object TableColumnSizer {
    private const val EPSILON = 0.5f

    fun compute(
        columnWeights: List<Float>,
        availableWidthDp: Float,
        config: TableLayoutConfig = TableLayoutConfig.Default
    ): TableLayoutResult {
        val count = columnWeights.size
        if (count == 0) return TableLayoutResult(emptyList(), 0f, false)

        val safeAvailable = availableWidthDp.coerceAtLeast(0f)
        val weightSum = columnWeights.sum().takeIf { it > 0f } ?: count.toFloat()
        val min = config.minColumnWidthDp
        val max = config.maxColumnWidthDp

        val widths = MutableList(count) { index ->
            val base = safeAvailable * (columnWeights[index] / weightSum)
            base.coerceIn(min, max)
        }
        distributeSlack(widths, safeAvailable, max)

        val total = widths.sum()
        return TableLayoutResult(
            columnWidthsDp = widths,
            totalWidthDp = maxOf(total, safeAvailable),
            requiresScroll = total > safeAvailable + EPSILON
        )
    }

    /**
     * Water-filling: hand leftover horizontal space to columns that are still
     * below [max], so wide screens stretch to the full available width.
     */
    private fun distributeSlack(widths: MutableList<Float>, available: Float, max: Float) {
        var slack = available - widths.sum()
        if (slack <= EPSILON) return

        var iterations = 0
        while (slack > EPSILON && iterations < widths.size) {
            val expandable = widths.indices.filter { widths[it] < max - EPSILON }
            if (expandable.isEmpty()) return

            val share = slack / expandable.size
            var consumed = 0f
            expandable.forEach { index ->
                val room = (max - widths[index]).coerceAtLeast(0f)
                val add = minOf(share, room)
                widths[index] += add
                consumed += add
            }
            if (consumed <= EPSILON) return
            slack -= consumed
            iterations++
        }
    }
}
