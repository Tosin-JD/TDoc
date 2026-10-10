package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.resolve.DocxParagraphProperties
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.Border
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
        val style = TableStyle(
            styleId = tblPr?.wFirst("tblStyle")?.wAttr("val"),
            alignment = DocxParagraphProperties.alignment(tblPr?.wFirst("jc")?.wAttr("val")),
            widthPoints = Units.twipsToPoints(tblPr?.wFirst("tblW")?.wAttr("w")?.toIntOrNull()),
            borders = parseBorders(tblPr?.wFirst("tblBorders")),
            shading = tblPr?.wFirst("shd")?.let {
                Shading(fill = it.wAttr("fill"), color = it.wAttr("color"))
            },
            layout = tblPr?.wFirst("tblLayout")?.wAttr("type"),
            caption = tblPr?.wFirst("tblCaption")?.wAttr("val"),
            description = tblPr?.wFirst("tblDescription")?.wAttr("val")
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

    private fun parseRow(row: Element): BlockNode.TableRow {
        val trPr = row.wFirst("trPr")
        val isHeader = trPr?.wFirst("tblHeader") != null
        val height = Units.twipsToPoints(trPr?.wFirst("trHeight")?.wAttr("val")?.toIntOrNull())
        val cells = row.w("tc").map { parseCell(it) }
        return BlockNode.TableRow(
            cells = cells,
            isHeader = isHeader,
            heightPoints = height,
            anchor = SourceAnchor(context.part, "w:tr")
        )
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
            borders = parseBorders(tcPr?.wFirst("tcBorders")),
            shading = tcPr?.wFirst("shd")?.let {
                Shading(fill = it.wAttr("fill"), color = it.wAttr("color"))
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

    private fun parseBorders(parent: Element?): CellBorders {
        if (parent == null) return CellBorders()
        fun border(name: String): Border? {
            val element = parent.wFirst(name) ?: return null
            return Border(
                style = element.wAttr("val"),
                widthPoints = (element.wAttr("sz")?.toIntOrNull() ?: 0) / 8.0,
                color = element.wAttr("color")?.takeUnless { it.equals("auto", true) },
                space = Units.twipsToPoints(element.wAttr("space")?.toIntOrNull())
            )
        }
        return CellBorders(
            top = border("top"),
            right = border("right"),
            bottom = border("bottom"),
            left = border("left")
        )
    }
}
