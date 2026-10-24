package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.w3c.dom.Element

/** Maps `table:table` into the domain table model, expanding repeats/spans. */
class OdfTableMapper(private val context: OdfMapContext) {

    lateinit var blockMapper: OdfBlockMapper
        private set

    fun attach(blockMapper: OdfBlockMapper) {
        this.blockMapper = blockMapper
    }

    fun parseTable(table: Element): BlockNode.Table {
        val styleName = table.attrNs(Ns.TEXT, "style-name")
        val rows = mutableListOf<BlockNode.TableRow>()
        val gridColumns = mutableListOf<Double?>()
        val spans = mutableListOf<CellSpan>()
        var rowIndex = 0
        val children = table.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            when {
                child.matchesNs(Ns.TABLE, "table-column") -> gridColumns += parseColumn(child)
                child.matchesNs(Ns.TABLE, "table-columns") ->
                    child.elementsNs(Ns.TABLE, "table-column").forEach { gridColumns += parseColumn(it) }
                child.matchesNs(Ns.TABLE, "table-row") -> {
                    val parsed = parseRow(child, isHeader = false, rowIndex, spans)
                    rows += expand(parsed, repeatOf(child), "table:table-row")
                    rowIndex += parsed.cells.size * repeatOf(child)
                }
                child.matchesNs(Ns.TABLE, "table-header-rows") ->
                    child.elementsNs(Ns.TABLE, "table-row").forEach {
                        val parsed = parseRow(it, isHeader = true, rowIndex, spans)
                        rows += expand(parsed, repeatOf(it), "table:table-header-rows")
                        rowIndex += parsed.cells.size * repeatOf(it)
                    }
                child.matchesNs(Ns.TABLE, "table-row-group") ->
                    child.elementsNs(Ns.TABLE, "table-row").forEach {
                        val parsed = parseRow(it, isHeader = false, rowIndex, spans)
                        rows += expand(parsed, repeatOf(it), "table:table-row-group")
                        rowIndex += parsed.cells.size * repeatOf(it)
                    }
            }
        }
        val tableStyle = context.styles.tableStyle(styleName).let { style ->
            val caption = table.firstNs(Ns.TABLE, "caption")?.textContent?.trim()
                ?.takeIf { it.isNotEmpty() } ?: table.attrNs(Ns.TABLE, "name")
            val description = table.firstNs(Ns.TABLE, "description")?.textContent?.trim()
                ?.takeIf { it.isNotEmpty() }
            style.copy(
                caption = caption ?: style.caption,
                description = description ?: style.description
            )
        }
        return BlockNode.Table(
            style = tableStyle,
            gridColumns = gridColumns,
            rows = propagateRowSpans(rows, spans),
            anchor = SourceAnchor(context.part, "table:table")
        )
    }

    private data class CellSpan(val row: Int, val col: Int, val rowSpan: Int)

    /**
     * Producers may omit `table:covered-table-cell` placeholders for cells
     * covered by `number-rows-spanned`; insert CONTINUE cells so covered
     * positions stay aligned. Rows that already carry a covered/restart cell
     * at that position are left untouched.
     */
    private fun propagateRowSpans(
        rows: List<BlockNode.TableRow>,
        spans: List<CellSpan>
    ): List<BlockNode.TableRow> {
        if (spans.none { it.rowSpan > 1 }) return rows
        val result = rows.toMutableList()
        for (span in spans) {
            if (span.rowSpan <= 1) continue
            for (offset in 1 until span.rowSpan) {
                val target = span.row + offset
                if (target >= result.size) break
                val cells = result[target].cells.toMutableList()
                when (cells.getOrNull(span.col)?.style?.verticalMerge) {
                    null -> {
                        while (cells.size < span.col) {
                            cells += coveredCell()
                        }
                        cells.add(span.col, coveredCell())
                    }
                    VerticalMerge.NONE -> {
                        context.diagnostics.info(
                            "table:table-cell",
                            "row-span covered position holds content; keeping content"
                        )
                    }
                    else -> Unit // already CONTINUE/RESTART — aligned
                }
                result[target] = result[target].copy(cells = cells)
            }
        }
        return result
    }

    private fun coveredCell(): BlockNode.TableCell = BlockNode.TableCell(
        blocks = emptyList(),
        style = CellStyle(verticalMerge = VerticalMerge.CONTINUE),
        anchor = SourceAnchor(context.part, "table:covered-table-cell")
    )

    /** One grid column: the column style's width, expanded per `number-columns-repeated`. */
    private fun parseColumn(column: Element): List<Double?> {
        val width = context.styles.columnWidth(column.attrNs(Ns.TABLE, "style-name"))
        val repeat = repeatOf(column)
        if (repeat > REPEAT_CAP) {
            context.diagnostics.info(
                "table:table-column",
                "number-columns-repeated=$repeat capped at $REPEAT_CAP"
            )
        }
        return List(repeat.coerceAtMost(REPEAT_CAP)) { width }
    }

    private fun repeatOf(element: Element): Int =
        element.attrNs(Ns.TABLE, "number-columns-repeated")?.toIntOrNull()?.coerceAtLeast(1)
            ?: element.attrNs(Ns.TABLE, "number-rows-repeated")?.toIntOrNull()?.coerceAtLeast(1)
            ?: 1

    private fun expand(row: BlockNode.TableRow, repeat: Int, construct: String): List<BlockNode.TableRow> {
        val count = repeat.coerceAtMost(REPEAT_CAP)
        if (repeat > REPEAT_CAP) {
            context.diagnostics.info(construct, "number-rows-repeated=$repeat capped at $REPEAT_CAP")
        }
        return List(count) { row }
    }

    private fun parseRow(
        row: Element,
        isHeader: Boolean,
        rowIndex: Int,
        spans: MutableList<CellSpan>
    ): BlockNode.TableRow {
        val cells = mutableListOf<BlockNode.TableCell>()
        var columnIndex = 0
        val children = row.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            when {
                child.matchesNs(Ns.TABLE, "covered-table-cell") -> {
                    cells += emptyCell(child)
                    columnIndex++
                }
                child.matchesNs(Ns.TABLE, "table-cell") -> {
                    val repeat = repeatOf(child)
                    val gridSpan = child.attrNs(Ns.TABLE, "number-columns-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val rowSpan = child.attrNs(Ns.TABLE, "number-rows-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    if (rowSpan > 1) {
                        spans += CellSpan(rowIndex, columnIndex, rowSpan)
                    }
                    cells += parseCell(child)
                    columnIndex += gridSpan * repeat
                }
            }
        }
        return BlockNode.TableRow(
            cells = cells,
            isHeader = isHeader,
            heightPoints = context.styles.rowHeight(row.attrNs(Ns.TABLE, "style-name")),
            anchor = SourceAnchor(context.part, "table:table-row")
        )
    }

    private companion object {
        /** Guard against pathological repeat counts in hostile/broken files. */
        const val REPEAT_CAP = 64
    }

    private fun parseCell(cell: Element): List<BlockNode.TableCell> {
        val repeat = cell.attrNs(Ns.TABLE, "number-columns-repeated")?.toIntOrNull()?.coerceIn(1, 512) ?: 1
        val gridSpan = cell.attrNs(Ns.TABLE, "number-columns-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rowSpan = cell.attrNs(Ns.TABLE, "number-rows-spanned")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val style = context.styles.cellStyle(cell.attrNs(Ns.TABLE, "style-name"))
        val blocks = blockMapper.parseBlocks(cell)
        val anchor = SourceAnchor(context.part, "table:table-cell")
        val base = style.copy(
            gridSpan = gridSpan,
            verticalMerge = if (rowSpan > 1) VerticalMerge.RESTART else VerticalMerge.NONE
        )
        return (0 until repeat).map { index ->
            if (index == 0) BlockNode.TableCell(blocks, base, anchor)
            else BlockNode.TableCell(emptyList(), CellStyle(), anchor)
        }
    }

    private fun emptyCell(cell: Element): BlockNode.TableCell {
        val style = context.styles.cellStyle(cell.attrNs(Ns.TABLE, "style-name"))
        return BlockNode.TableCell(
            blocks = emptyList(),
            style = style.copy(verticalMerge = VerticalMerge.CONTINUE),
            anchor = SourceAnchor(context.part, "table:covered-table-cell")
        )
    }
}
