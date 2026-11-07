package com.tosin.docprocessor.ui.components.table.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tosin.docprocessor.data.parser.internal.models.TextSpan

/**
 * Immutable, presentation-ready representation of a document table.
 *
 * Produced by [com.tosin.docprocessor.ui.components.table.TableUiMapper] so the
 * Compose layer never has to reason about ragged source rows.
 */
@Immutable
data class TableUiModel(
    val columnCount: Int,
    val rows: List<TableRowUi>,
    val columnWeights: List<Float>
) {
    val isEmpty: Boolean get() = rows.isEmpty() || columnCount == 0
}

@Immutable
data class TableRowUi(
    val cells: List<TableCellUi>,
    val isHeader: Boolean
)

@Immutable
data class TableCellUi(
    val text: String,
    val alignment: CellTextAlignment = CellTextAlignment.START,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val isCovered: Boolean = false,
    val backgroundColor: Color? = null,
    val spans: List<TextSpan> = emptyList()
)

enum class CellTextAlignment { START, CENTER, END }
