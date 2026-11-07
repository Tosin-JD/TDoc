package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.RichTableCell
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.model.DocumentElement
import org.w3c.dom.Element

class OdtTableParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties> = emptyMap()
) {

    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"

    fun parseTable(element: Element): DocumentElement.Table {
        val defaultBorder = Border(style = "solid", widthPoints = 0.5, color = null)
        val tableBorders = CellBorders(
            top = defaultBorder,
            right = defaultBorder,
            bottom = defaultBorder,
            left = defaultBorder
        )

        val rows = mutableListOf<List<String>>()
        val metadataRows = mutableListOf<List<TableCellMetadata>>()
        val richRows = mutableListOf<List<RichTableCell>>()

        // Only direct rows belong to this table. Descendant queries accidentally
        // pull rows from nested tables into their parent.
        directChildren(element, "table-row").forEach { rowElement ->
            val cells = mutableListOf<String>()
            val cellMetadata = mutableListOf<TableCellMetadata>()
            val richCells = mutableListOf<RichTableCell>()

            directChildren(rowElement, null).forEach { cellElement ->
                if (cellElement.localName == "covered-table-cell") {
                    cells += ""
                    cellMetadata += TableCellMetadata(horizontalMerge = "covered")
                    richCells += RichTableCell(
                        spans = emptyList(),
                        isCovered = true,
                        borders = tableBorders
                    )
                    return@forEach
                }
                if (cellElement.localName != "table-cell") return@forEach
                val cellText = cellElement.textContent.trim()
                val span = cellElement.getAttributeNS(tableNs, "number-columns-spanned").toIntOrNull() ?: 1
                val rowSpan = cellElement.getAttributeNS(tableNs, "number-rows-spanned").toIntOrNull() ?: 1
                val cellColor = styles[cellElement.getAttributeNS(tableNs, "style-name")]?.color
                repeat(span) { index ->
                    cells.add(cellText)
                    cellMetadata += TableCellMetadata(
                        gridSpan = span.takeIf { it > 1 },
                        verticalMerge = cellElement.getAttributeNS(tableNs, "number-rows-spanned").takeIf { it.isNotBlank() },
                        shadingColor = cellColor
                    )
                    richCells += RichTableCell(
                        spans = if (index == 0) listOf(TextSpan(text = cellText)) else emptyList(),
                        colSpan = if (index == 0) span else 1,
                        rowSpan = if (index == 0) rowSpan else 1,
                        isCovered = index > 0,
                        backgroundColor = cellColor,
                        borders = tableBorders
                    )
                }
            }
            val repeat = rowElement.getAttributeNS(tableNs, "number-rows-repeated").toIntOrNull()?.coerceAtLeast(1) ?: 1
            repeat(repeat) {
                rows += cells.toList()
                metadataRows += cellMetadata.toList()
                richRows += richCells.toList()
            }
        }

        val tableStyleName = element.getAttributeNS(tableNs, "style-name")
        val tableStyle = styles[tableStyleName]

        return DocumentElement.Table(
            rows = rows,
            hasHeader = false,
            metadata = TableMetadata(
                styleId = tableStyleName.takeIf { it.isNotEmpty() } ?: "Default",
                shadingColor = tableStyle?.color, // Simplification: ODT table shading is usually in table-cell-properties
                caption = null,
                description = null,
                borderSummary = null,
                borders = tableBorders,
                cellMargins = EdgeInsets(),
                rows = metadataRows
            ),
            richRows = richRows
        )
    }

    private fun directChildren(parent: Element, localName: String?): List<Element> =
        (0 until parent.childNodes.length)
            .asSequence()
            .mapNotNull { parent.childNodes.item(it) as? Element }
            .filter { it.namespaceURI == tableNs && (localName == null || it.localName == localName) }
            .toList()
}
