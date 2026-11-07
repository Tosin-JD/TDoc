package com.tosin.docprocessor.ui.components.table

import androidx.compose.ui.graphics.Color
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.data.parser.internal.models.RichTableCell
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.pagination.table.TableMergeResolver
import com.tosin.docprocessor.domain.rendering.table.TableCellPresentation
import com.tosin.docprocessor.domain.rendering.table.TablePresentation
import com.tosin.docprocessor.ui.components.table.model.CellTextAlignment
import com.tosin.docprocessor.ui.components.table.model.TableCellUi
import com.tosin.docprocessor.ui.components.table.model.TableRowUi
import com.tosin.docprocessor.ui.components.table.model.TableUiModel

/**
 * Maps a [DocumentElement.Table] into a rectangular, presentation-ready
 * [TableUiModel].
 *
 * When the element carries a resolved [TablePresentation] (produced by
 * `TablePresentationResolver` from the rich document model) the mapper is a
 * faithful projection: shading, borders, margins, spans and alignment are
 * already resolved and are never augmented with application styling.
 *
 * Otherwise it falls back to the legacy `richRows`/plain-text projection so
 * tables built without the rich model still render their content.
 */
class TableUiMapper {

    fun map(table: DocumentElement.Table): TableUiModel {
        table.presentation?.let { return mapPresentation(table, it) }
        return mapLegacy(table)
    }

    // ------------------------------------------------------------------
    // Resolved presentation path (document-authored styling)
    // ------------------------------------------------------------------

    private fun mapPresentation(
        table: DocumentElement.Table,
        presentation: TablePresentation
    ): TableUiModel {
        if (presentation.isEmpty) {
            return TableUiModel(columnCount = 0, rows = emptyList(), columnWeights = emptyList())
        }
        val columnCount = presentation.columnCount
        val rows = presentation.rows.map { row ->
            TableRowUi(
                cells = row.cells.map { it.toUi(plainRow = null) },
                isHeader = row.isHeader
            )
        }
        return TableUiModel(
            columnCount = columnCount,
            rows = rows,
            columnWeights = resolveWeights(presentation),
            tableBackground = parseColor(presentation.shading?.fill),
            tableBackgroundSource = presentation.shading?.fill,
            outerBorders = null,
            isStriped = false,
            sourceWidthsPt = presentation.gridColumnWidthsPt.mapNotNull { it?.toFloat() },
            preferredWidthPt = presentation.widthPoints?.toFloat(),
            tableAlignment = presentation.alignment.toCellAlignment()
        )
    }

    private fun TableCellPresentation.toUi(plainRow: List<String>?): TableCellUi {
        val text = spans.joinToString("") { it.text }
        return TableCellUi(
            text = text,
            alignment = horizontalAlign.toCellAlignment(),
            colSpan = colSpan.coerceAtLeast(1),
            rowSpan = rowSpan.coerceAtLeast(1),
            isCovered = isCovered,
            backgroundColor = parseColor(shading?.fill),
            backgroundColorSource = shading?.fill,
            spans = spans,
            borders = paintBorders,
            paintBordersResolved = true,
            margins = margins,
            verticalAlignment = verticalAlign ?: VerticalCellAlign.TOP
        )
    }

    private fun resolveWeights(presentation: TablePresentation): List<Float> {
        val columnCount = presentation.columnCount
        val grid = presentation.gridColumnWidthsPt
        if (grid.size == columnCount && grid.all { it != null } && grid.sumOf { it!! } > 0.0) {
            return grid.map { it!!.toFloat().coerceAtLeast(1f) }
        }
        val weights = MutableList(columnCount) { MIN_COLUMN_WEIGHT }
        presentation.rows.forEach { row ->
            row.cells.forEach { cell ->
                if (cell.isCovered) return@forEach
                val weight = estimateWeight(cell.spans.joinToString("") { it.text })
                for (offset in 0 until cell.colSpan.coerceAtLeast(1)) {
                    val index = cell.columnIndex + offset
                    if (index in weights.indices) {
                        weights[index] = maxOf(weights[index], weight)
                    }
                }
            }
        }
        return weights
    }

    // ------------------------------------------------------------------
    // Legacy projection path (no resolved presentation available)
    // ------------------------------------------------------------------

    private fun mapLegacy(table: DocumentElement.Table): TableUiModel {
        val richRows = table.richRows.takeIf { it.isNotEmpty() }?.let(TableMergeResolver::resolve)
        val columnCount = columnCount(table, richRows)
        if (columnCount == 0) {
            return TableUiModel(columnCount = 0, rows = emptyList(), columnWeights = emptyList())
        }

        val rowCount = maxOf(table.rows.size, richRows?.size ?: 0)
        val rows = (0 until rowCount).map { rowIndex ->
            val isHeader = table.hasHeader && rowIndex == 0
            TableRowUi(
                cells = buildRow(
                    rowIndex = rowIndex,
                    columnCount = columnCount,
                    richRow = richRows?.getOrNull(rowIndex),
                    plainRow = table.rows.getOrNull(rowIndex),
                    isHeader = isHeader
                ),
                isHeader = isHeader
            )
        }

        val weights = resolveColumnWeights(table, richRows, columnCount)

        val tableBorders = table.metadata.borders
        val hasTableBorders = tableBorders.hasAny()
        return TableUiModel(
            columnCount = columnCount,
            rows = rows,
            columnWeights = weights,
            tableBackground = parseColor(table.metadata.shadingColor),
            tableBackgroundSource = table.metadata.shadingColor,
            outerBorders = tableBorders.takeIf { hasTableBorders },
            isStriped = false,
            sourceWidthsPt = table.gridColumnWidthsPt
        )
    }

    private fun columnCount(
        table: DocumentElement.Table,
        richRows: List<List<RichTableCell>>?
    ): Int {
        val richColumns = richRows?.maxOfOrNull { row ->
            row.sumOf { it.colSpan.coerceAtLeast(1) }
        } ?: 0
        val plainColumns = table.rows.maxOfOrNull { it.size } ?: 0
        return maxOf(richColumns, plainColumns, table.gridColumnWidthsPt.size, 0)
    }

    private fun buildRow(
        rowIndex: Int,
        columnCount: Int,
        richRow: List<RichTableCell>?,
        plainRow: List<String>?,
        isHeader: Boolean
    ): List<TableCellUi> {
        val cells = MutableList<TableCellUi?>(columnCount) { null }

        if (richRow != null) {
            var column = 0
            richRow.forEach { richCell ->
                if (column >= columnCount) return@forEach
                val colSpan = richCell.colSpan.coerceIn(1, columnCount - column)
                val cell = richCell.toUi(
                    plainText = plainRow?.getOrNull(column),
                    colSpan = colSpan
                ).let { ui ->
                    // Header rows are emphasised with bold text.
                    if (isHeader) {
                        ui.copy(spans = ui.spans.map { it.copy(isBold = true) })
                    } else {
                        ui
                    }
                }
                cells[column] = cell

                // Horizontal-span placeholders keep the row rectangular so the
                // renderer can address cells by column index.
                for (offset in 1 until colSpan) {
                    cells[column + offset] = cell.copy(
                        text = "",
                        spans = emptyList(),
                        colSpan = 1,
                        rowSpan = 1,
                        isCovered = true,
                        backgroundColor = null,
                        borders = null,
                        margins = null
                    )
                }
                column += colSpan
            }
        }

        // Fill remaining slots from the plain-text projection (also the whole row
        // for tables that carry no rich data).
        for (column in 0 until columnCount) {
            if (cells[column] == null) {
                cells[column] = TableCellUi(text = plainRow?.getOrNull(column).orEmpty())
            }
        }

        return cells.map { it ?: TableCellUi(text = "") }
    }

    private fun RichTableCell.toUi(plainText: String?, colSpan: Int): TableCellUi {
        val text = spans.joinToString("") { it.text }.ifEmpty { plainText.orEmpty() }
        return TableCellUi(
            text = text,
            alignment = horizontalAlignment.toCellAlignment(),
            colSpan = colSpan,
            rowSpan = rowSpan.coerceAtLeast(1),
            isCovered = isCovered,
            backgroundColor = parseColor(backgroundColor),
            backgroundColorSource = backgroundColor,
            spans = spans,
            borders = borders,
            margins = margins,
            verticalAlignment = verticalAlignment
        )
    }

    private fun resolveColumnWeights(
        table: DocumentElement.Table,
        richRows: List<List<RichTableCell>>?,
        columnCount: Int
    ): List<Float> {
        val grid = table.gridColumnWidthsPt
        if (grid.size == columnCount && grid.sum() > 0f) {
            return grid.map { it.coerceAtLeast(1f) }
        }

        val weights = MutableList(columnCount) { MIN_COLUMN_WEIGHT }
        table.rows.forEach { row ->
            row.forEachIndexed { column, text ->
                if (column < columnCount) {
                    weights[column] = maxOf(weights[column], estimateWeight(text))
                }
            }
        }
        richRows?.forEach { row ->
            var column = 0
            row.forEach { cell ->
                val span = cell.colSpan.coerceAtLeast(1)
                val weight = estimateWeight(cell.spans.joinToString("") { it.text })
                for (offset in 0 until span) {
                    val index = column + offset
                    if (index in 0 until columnCount) {
                        weights[index] = maxOf(weights[index], weight)
                    }
                }
                column += span
            }
        }
        return weights
    }

    private fun estimateWeight(text: String): Float {
        val longest = text
            .lineSequence()
            .maxOfOrNull { it.trim().length }
            ?.coerceAtLeast(1)
            ?: 1
        return (longest / CHARACTERS_PER_WEIGHT_UNIT).coerceIn(MIN_COLUMN_WEIGHT, MAX_COLUMN_WEIGHT)
    }

    private fun parseColor(hex: String?): Color? =
        DocumentTextColorResolver.parseColorString(hex)?.let { Color(it) }

    private fun TextAlignment?.toCellAlignment(): CellTextAlignment = when (this) {
        TextAlignment.CENTER -> CellTextAlignment.CENTER
        TextAlignment.END -> CellTextAlignment.END
        else -> CellTextAlignment.START
    }

    private companion object {
        const val MIN_COLUMN_WEIGHT = 1f
        const val MAX_COLUMN_WEIGHT = 8f
        const val CHARACTERS_PER_WEIGHT_UNIT = 8f
    }
}
