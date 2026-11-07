package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.resolve.DocxBorderProperties
import com.tosin.docprocessor.data.parser.docx.resolve.DocxParagraphProperties
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.w3c.dom.Element

/** Maps `w:tbl`/`w:tr`/`w:tc` into a nested [BlockNode.Table]. */
class DocxTableMapper(
    private val context: DocxMapContext,
    private val blockMapper: DocxBlockMapper
) {

    fun parseTable(table: Element): BlockNode.Table {
        val tblPr = table.wFirst("tblPr")
        val styleId = tblPr?.wFirst("tblStyle")?.wAttr("val")
        val tblBorders = tblPr?.wFirst("tblBorders")
        val localBorders = DocxBorderProperties.parseCellBorders(tblBorders)
        val localInsideBorders = DocxBorderProperties.parseInsideBorders(tblBorders)
        val resolved = if (styleId != null) {
            context.styles.tableStyle(styleId)
        } else if (!localBorders.hasAny() && localInsideBorders == null) {
            context.styles.tableStyle("TableGrid")
        } else {
            context.styles.tableStyle(null)
        }
        val localMargins = DocxBorderProperties.parseCellMargins(tblPr?.wFirst("tblCellMar"))
        val style = resolved.copy(
            styleId = styleId ?: resolved.styleId,
            alignment = DocxParagraphProperties.alignment(tblPr?.wFirst("jc")?.wAttr("val"))
                ?: resolved.alignment,
            widthPoints = Units.twipsToPoints(tblPr?.wFirst("tblW")?.wAttr("w")?.toIntOrNull())
                ?: resolved.widthPoints,
            // Direct formatting wins per side; unspecified sides keep the
            // inherited table-style value (including explicit `nil` markers).
            borders = resolved.borders.overlay(localBorders),
            insideBorders = localInsideBorders ?: resolved.insideBorders,
            cellMargins = mergeNullable(resolved.cellMargins, localMargins),
            shading = tblPr?.wFirst("shd")?.let {
                Shading(
                    fill = it.wAttr("fill")?.takeUnless { v -> v.equals("auto", true) },
                    color = it.wAttr("color")?.takeUnless { v -> v.equals("auto", true) }
                )
            } ?: resolved.shading,
            layout = tblPr?.wFirst("tblLayout")?.wAttr("type") ?: resolved.layout,
            caption = tblPr?.wFirst("tblCaption")?.wAttr("val") ?: resolved.caption,
            description = tblPr?.wFirst("tblDescription")?.wAttr("val") ?: resolved.description
        )
        val gridColumns = table.w("tblGrid").firstOrNull()
            ?.w("gridCol")
            ?.map { Units.twipsToPoints(it.wAttr("w")?.toIntOrNull()) }
            .orEmpty()
        val rows = table.w("tr").map { parseRow(it) }
        context.diagnostics.info("w:tbl", "Mapped table with ${rows.size} rows")
        return BlockNode.Table(
            style = style,
            gridColumns = gridColumns,
            rows = rows,
            anchor = SourceAnchor(context.part, "w:tbl")
        )
    }

    /** Per-side merge for nullable [CellBorders] (cell margins). */
    private fun mergeNullable(base: CellBorders?, over: CellBorders?): CellBorders? = when {
        over == null -> base
        base == null -> over
        else -> base.overlay(over)
    }

    private fun parseRow(row: Element): BlockNode.TableRow {
        val trPr = row.wFirst("trPr")
        val isHeader = trPr?.wFirst("tblHeader") != null
        val height = Units.twipsToPoints(trPr?.wFirst("trHeight")?.wAttr("val")?.toIntOrNull())
        val cells = collapseHorizontalMerge(row.w("tc").map { parseCellWithMerge(it) })
        return BlockNode.TableRow(
            cells = cells,
            isHeader = isHeader,
            heightPoints = height,
            anchor = SourceAnchor(context.part, "w:tr")
        )
    }

    private data class CellWithMerge(val cell: BlockNode.TableCell, val hMerge: String?)

    /**
     * `w:hMerge` marks the anchor (`restart`) and its covered cells
     * (`continue`/`cont`); covered cells are dropped and counted into the
     * anchor's grid span, mirroring how `w:vMerge` is handled.
     */
    private fun collapseHorizontalMerge(cells: List<CellWithMerge>): List<BlockNode.TableCell> {
        val output = mutableListOf<BlockNode.TableCell>()
        var anchorIndex = -1
        var spanned = 0
        for (entry in cells) {
            when (entry.hMerge) {
                "restart" -> {
                    flushHorizontalSpan(output, anchorIndex, spanned)
                    output += entry.cell
                    anchorIndex = output.lastIndex
                    spanned = 0
                }
                "continue", "cont" -> {
                    if (anchorIndex >= 0) spanned++
                    else {
                        context.diagnostics.warning(
                            "w:hMerge", "continuation cell without a restart anchor treated as an ordinary cell"
                        )
                        output += entry.cell
                    }
                }
                else -> {
                    flushHorizontalSpan(output, anchorIndex, spanned)
                    output += entry.cell
                    anchorIndex = -1
                    spanned = 0
                }
            }
        }
        flushHorizontalSpan(output, anchorIndex, spanned)
        return output
    }

    private fun flushHorizontalSpan(cells: MutableList<BlockNode.TableCell>, anchorIndex: Int, spanned: Int) {
        if (anchorIndex >= 0 && spanned > 0) {
            val anchor = cells[anchorIndex]
            cells[anchorIndex] = anchor.copy(
                style = anchor.style.copy(gridSpan = anchor.style.gridSpan + spanned)
            )
        }
    }

    private fun parseCellWithMerge(cell: Element): CellWithMerge {
        val tcPr = cell.wFirst("tcPr")
        val hMerge = tcPr?.wFirst("hMerge")?.wAttr("val") ?: if (tcPr?.wFirst("hMerge") != null) "restart" else null
        return CellWithMerge(parseCell(cell), hMerge)
    }

    private fun parseCell(cell: Element): BlockNode.TableCell {
        val tcPr = cell.wFirst("tcPr")
        val gridSpan = tcPr?.wFirst("gridSpan")?.wAttr("val")?.toIntOrNull() ?: 1
        val vMerge = when (val element = tcPr?.wFirst("vMerge")) {
            null -> VerticalMerge.NONE
            else -> if (element.wAttr("val") == null || element.wAttr("val") == "continue") {
                VerticalMerge.CONTINUE
            } else VerticalMerge.RESTART
        }
        val tcW = tcPr?.wFirst("tcW")
        val width = tcW?.wAttr("w")?.toIntOrNull()?.let { Units.twipsToPoints(it) }
        val style = CellStyle(
            widthPoints = width,
            gridSpan = gridSpan,
            verticalMerge = vMerge,
            borders = DocxBorderProperties.parseCellBorders(tcPr?.wFirst("tcBorders")),
            margins = DocxBorderProperties.parseCellMargins(tcPr?.wFirst("tcMar")),
            shading = tcPr?.wFirst("shd")?.let {
                Shading(
                    fill = it.wAttr("fill")?.takeUnless { v -> v.equals("auto", true) },
                    color = it.wAttr("color")?.takeUnless { v -> v.equals("auto", true) }
                )
            },
            verticalAlign = when (tcPr?.wFirst("vAlign")?.wAttr("val")?.lowercase()) {
                "top" -> VerticalCellAlign.TOP
                "center" -> VerticalCellAlign.CENTER
                "bottom" -> VerticalCellAlign.BOTTOM
                else -> null
            },
            textDirection = tcPr?.wFirst("textDirection")?.wAttr("val")
        )
        return BlockNode.TableCell(
            blocks = blockMapper.parseBlocks(cell),
            style = style,
            anchor = SourceAnchor(context.part, "w:tc")
        )
    }

}
