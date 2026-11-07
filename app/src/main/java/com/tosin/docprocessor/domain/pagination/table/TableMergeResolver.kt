package com.tosin.docprocessor.domain.pagination.table

import com.tosin.docprocessor.data.parser.internal.models.RichTableCell

/**
 * Resolves the logical table grid from raw cells.
 *
 * Responsibilities:
 * - collapse explicit `hMerge` continuations into their anchor's [RichTableCell.colSpan];
 * - derive [RichTableCell.rowSpan] for cells that restart a vertical merge and the
 *   number of following rows that continue it;
 * - mark all covered/hMerge/vMerge continuation positions with
 *   [RichTableCell.isCovered] so renderers can skip them.
 *
 * The returned rows keep the same logical shape as the input (spanned rows are not
 * expanded into extra placeholder cells); consumers advance by `colSpan` when they
 * need an actual column index.
 */
object TableMergeResolver {

    private data class Anchor(
        val row: Int,
        val cellIndex: Int,
        val column: Int,
        var rowSpan: Int = 1
    )

    fun resolve(rows: List<List<RichTableCell>>): List<List<RichTableCell>> {
        if (rows.isEmpty()) return emptyList()

        val collapsed = rows.map(::collapseHorizontalMerges)
        val output = collapsed.map { it.toMutableList() }
        val starts = collapsed.map(::columnStarts)

        // Active vertical merge anchors keyed by their starting grid column.
        val active = mutableMapOf<Int, Anchor>()

        fun finalize(column: Int) {
            val anchor = active.remove(column) ?: return
            if (anchor.rowSpan > 1) {
                val anchorCell = output[anchor.row].getOrNull(anchor.cellIndex) ?: return
                output[anchor.row][anchor.cellIndex] = anchorCell.copy(rowSpan = anchor.rowSpan)
            }
        }

        collapsed.forEachIndexed { rowIndex, row ->
            val seenColumns = mutableSetOf<Int>()
            row.forEachIndexed { cellIndex, cell ->
                val startColumn = starts[rowIndex][cellIndex]
                seenColumns += startColumn
                when (cell.metadata.verticalMerge?.lowercase()) {
                    "restart" -> {
                        finalize(startColumn)
                        active[startColumn] = Anchor(rowIndex, cellIndex, startColumn)
                    }

                    "continue", "cont" -> {
                        val anchor = active[startColumn]
                        if (anchor != null) {
                            anchor.rowSpan++
                            output[rowIndex][cellIndex] = cell.copy(isCovered = true, rowSpan = 1)
                        } else {
                            // Orphaned continuation (broken producer): leave it visible rather
                            // than hiding a cell with no anchor above it.
                            output[rowIndex][cellIndex] = cell.copy(isCovered = false, rowSpan = 1)
                        }
                    }

                    else -> finalize(startColumn)
                }
            }
            // A row that no longer carries a cell at an active start column closes the merge.
            active.keys.toList().filterNot { it in seenColumns }.forEach(::finalize)
        }

        active.keys.toList().forEach(::finalize)
        return output
    }

    /**
     * Collapses `hMerge` continuation cells into the preceding anchor and marks them
     * covered. Producers that already collapse `hMerge` (the DOCX mapper) pass through
     * unchanged.
     */
    private fun collapseHorizontalMerges(row: List<RichTableCell>): List<RichTableCell> {
        if (row.none { it.metadata.horizontalMerge != null }) return row

        val result = mutableListOf<RichTableCell>()
        var anchorIndex = -1
        var spanned = 0

        fun flush() {
            if (anchorIndex >= 0 && spanned > 0) {
                val anchor = result[anchorIndex]
                result[anchorIndex] = anchor.copy(colSpan = anchor.colSpan + spanned)
            }
            anchorIndex = -1
            spanned = 0
        }

        row.forEach { cell ->
            when (cell.metadata.horizontalMerge?.lowercase()) {
                "restart" -> {
                    flush()
                    result += cell
                    anchorIndex = result.lastIndex
                }

                "continue", "cont" -> {
                    if (anchorIndex >= 0) {
                        spanned++
                    } else {
                        result += cell.copy(isCovered = true)
                    }
                }

                else -> {
                    flush()
                    result += cell
                }
            }
        }
        flush()
        return result
    }

    /** Starting grid column for each logical cell, advancing by its horizontal span. */
    private fun columnStarts(row: List<RichTableCell>): List<Int> {
        var column = 0
        return row.map { cell ->
            val start = column
            column += cell.colSpan.coerceAtLeast(1)
            start
        }
    }
}
