package com.tosin.docprocessor.ui.components.table

import androidx.compose.ui.graphics.Color
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.components.table.model.CellTextAlignment
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
        val hasRich = table.richRows.isNotEmpty()

        val rows = table.rows.mapIndexed { index, cells ->
            val richRow = table.richRows.getOrNull(index)
            TableRowUi(
                cells = List(columnCount) { column ->
                    val richCell = richRow?.getOrNull(column)
                    if (richCell != null) {
                        val text = richCell.spans.joinToString("") { it.text }.ifEmpty { cells.getOrNull(column).orEmpty() }
                        val bgColor = richCell.backgroundColor?.let { hex ->
                            val colorInt = hex.toLongOrNull(16)?.toInt()
                            if (colorInt != null) {
                                if (hex.length == 6) Color(colorInt or -0x1000000) else Color(colorInt)
                            } else null
                        }
                        val alignment = when (richCell.horizontalAlignment) {
                            com.tosin.docprocessor.domain.document.model.style.TextAlignment.CENTER -> CellTextAlignment.CENTER
                            com.tosin.docprocessor.domain.document.model.style.TextAlignment.END -> CellTextAlignment.END
                            else -> CellTextAlignment.START
                        }
                        TableCellUi(
                            text = text,
                            alignment = alignment,
                            colSpan = richCell.colSpan,
                            rowSpan = richCell.rowSpan,
                            isCovered = richCell.isCovered,
                            backgroundColor = bgColor,
                            spans = richCell.spans
                        )
                    } else {
                        TableCellUi(text = cells.getOrNull(column).orEmpty())
                    }
                },
                isHeader = table.hasHeader && index == 0
            )
        }

        val weights = if (table.gridColumnWidthsPt.size == columnCount) {
            table.gridColumnWidthsPt.map { it.coerceAtLeast(1f) }
        } else {
            estimateWeights(table.rows, columnCount)
        }

        return TableUiModel(
            columnCount = columnCount,
            rows = rows,
            columnWeights = weights
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
