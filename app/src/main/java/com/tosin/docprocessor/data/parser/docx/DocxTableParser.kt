package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell

class DocxTableParser(
    private val paragraphParser: DocxParagraphParser = DocxParagraphParser()
) {

    fun parse(poiTable: XWPFTable): DocumentElement.Table {
        val cellMetadata = mutableListOf<List<TableCellMetadata>>()
        val tableRows = poiTable.rows.map { poiRow ->
            val rowMetadata = mutableListOf<TableCellMetadata>()
            val rowValues = poiRow.tableCells.map { poiCell ->
                val cellText = poiCell.paragraphs.joinToString("\n") { poiParagraph ->
                    val paragraph = paragraphParser.parse(poiParagraph)
                    buildString {
                        paragraph.listLabel?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                            append(' ')
                        }
                        append(paragraph.spans.joinToString("") { it.text })
                    }
                }
                rowMetadata += poiCell.toMetadata()
                cellText
            }
            cellMetadata += rowMetadata
            rowValues
        }

        val tablePr = poiTable.ctTbl.tblPr

        return DocumentElement.Table(
            rows = tableRows,
            hasHeader = poiTable.rows.firstOrNull()?.isRepeatHeader == true,
            metadata = TableMetadata(
                styleId = poiTable.styleID,
                caption = tablePr?.tblCaption?.`val`,
                description = tablePr?.tblDescription?.`val`,
                shadingColor = tablePr?.shd?.fill?.toString(),
                borderSummary = listOfNotNull(
                    tablePr?.tblBorders?.top?.color?.let { "top:$it" },
                    tablePr?.tblBorders?.bottom?.color?.let { "bottom:$it" },
                    tablePr?.tblBorders?.left?.color?.let { "left:$it" },
                    tablePr?.tblBorders?.right?.color?.let { "right:$it" }
                ).takeIf { it.isNotEmpty() }?.joinToString(),
                cellMargins = EdgeInsets(
                    top = tablePr?.tblCellMar?.top?.w.asInt(),
                    right = tablePr?.tblCellMar?.right?.w.asInt(),
                    bottom = tablePr?.tblCellMar?.bottom?.w.asInt(),
                    left = tablePr?.tblCellMar?.left?.w.asInt()
                ),
                rows = cellMetadata
            )
        )
    }

    private fun XWPFTableCell.toMetadata(): TableCellMetadata {
        val tcPr = ctTc.tcPr
        return TableCellMetadata(
            gridSpan = tcPr?.gridSpan?.`val`.asInt(),
            horizontalMerge = tcPr?.hMerge?.`val`?.toString(),
            verticalMerge = tcPr?.vMerge?.`val`?.toString(),
            shadingColor = tcPr?.shd?.fill?.toString(),
            borderSummary = listOfNotNull(
                tcPr?.tcBorders?.top?.color?.let { "top:$it" },
                tcPr?.tcBorders?.bottom?.color?.let { "bottom:$it" },
                tcPr?.tcBorders?.left?.color?.let { "left:$it" },
                tcPr?.tcBorders?.right?.color?.let { "right:$it" }
            ).takeIf { it.isNotEmpty() }?.joinToString(),
            margins = EdgeInsets(
                top = tcPr?.tcMar?.top?.w.asInt(),
                right = tcPr?.tcMar?.right?.w.asInt(),
                bottom = tcPr?.tcMar?.bottom?.w.asInt(),
                left = tcPr?.tcMar?.left?.w.asInt()
            ),
            nestedTableCount = tables.size
        )
    }

    private fun Any?.asInt(): Int? = when (this) {
        is Number -> toInt()
        else -> toString().toIntOrNull()
    }
}
