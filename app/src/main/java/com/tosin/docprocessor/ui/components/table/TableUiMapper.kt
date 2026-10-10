package com.tosin.docprocessor.ui.components.table

import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.components.table.model.TableCellUi
import com.tosin.docprocessor.ui.components.table.model.TableRowUi
import com.tosin.docprocessor.ui.components.table.model.TableUiModel

/**
 * Maps a [DocumentElement.Table] into a rectangular, presentation-ready
 * [TableUiModel]. Ragged rows are padded to the widest row so the rendered grid
 * never misaligns.
 */
class TableUiMapper {

    fun map(table: DocumentElement.Table): TableUiModel {
        if (table.rows.isEmpty()) {
            return TableUiModel(columnCount = 0, rows = emptyList(), columnWeights = emptyList())
        }

        val columnCount = table.rows.maxOf { it.size }.coerceAtLeast(1)

        val rows = table.rows.mapIndexed { index, cells ->
            TableRowUi(
                cells = List(columnCount) { column ->
                    TableCellUi(text = cells.getOrNull(column).orEmpty())
                },
                isHeader = table.hasHeader && index == 0
            )
        }

        return TableUiModel(
            columnCount = columnCount,
            rows = rows,
            columnWeights = estimateWeights(table.rows, columnCount)
        )
    }

    private fun estimateWeights(rows: List<List<String>>, columnCount: Int): List<Float> =
        List(columnCount) { column ->
            val longest = rows
                .mapNotNull { it.getOrNull(column) }
                .maxOfOrNull { line -> line.lineSequence().maxOfOrNull { it.trim().length } ?: 0 }
                ?.coerceAtLeast(1)
                ?: 1
            (longest / CHARACTERS_PER_WEIGHT_UNIT).coerceIn(MIN_COLUMN_WEIGHT, MAX_COLUMN_WEIGHT)
        }

    private companion object {
        const val MIN_COLUMN_WEIGHT = 1f
        const val MAX_COLUMN_WEIGHT = 8f
        const val CHARACTERS_PER_WEIGHT_UNIT = 8f
    }
}
