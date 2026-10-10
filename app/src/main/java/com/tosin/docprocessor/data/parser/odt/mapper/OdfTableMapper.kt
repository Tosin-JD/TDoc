package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
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
        val children = table.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            when {
                child.matchesNs(Ns.TABLE, "table-row") -> rows += parseRow(child, isHeader = false)
                child.matchesNs(Ns.TABLE, "table-header-rows") ->
                    child.elementsNs(Ns.TABLE, "table-row").forEach { rows += parseRow(it, isHeader = true) }
            }
        }
        return BlockNode.Table(
            style = context.styles.tableStyle(styleName),
            gridColumns = emptyList(),
            rows = rows,
            anchor = SourceAnchor(context.part, "table:table")
        )
    }

    private fun parseRow(row: Element, isHeader: Boolean): BlockNode.TableRow {
        val cells = mutableListOf<BlockNode.TableCell>()
        val children = row.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            when {
                child.matchesNs(Ns.TABLE, "covered-table-cell") -> cells += emptyCell(child)
                child.matchesNs(Ns.TABLE, "table-cell") -> cells += parseCell(child)
            }
        }
        return BlockNode.TableRow(cells = cells, isHeader = isHeader, anchor = SourceAnchor(context.part, "table:table-row"))
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
