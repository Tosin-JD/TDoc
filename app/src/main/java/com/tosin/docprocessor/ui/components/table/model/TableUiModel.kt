package com.tosin.docprocessor.ui.components.table.model

import androidx.compose.runtime.Immutable

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
    val alignment: CellTextAlignment = CellTextAlignment.START
)

enum class CellTextAlignment { START, CENTER, END }
