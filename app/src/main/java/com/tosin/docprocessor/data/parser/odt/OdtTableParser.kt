package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import org.w3c.dom.Element

import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets

class OdtTableParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties> = emptyMap()
) {

    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"

    fun parseTable(element: Element): DocumentElement.Table {
        val rows = mutableListOf<List<String>>()
        val metadataRows = mutableListOf<List<com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata>>()

        // Only direct rows belong to this table. Descendant queries accidentally
        // pull rows from nested tables into their parent.
        directChildren(element, "table-row").forEach { rowElement ->
            val cells = mutableListOf<String>()
            val cellMetadata = mutableListOf<com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata>()

            directChildren(rowElement, null).forEach { cellElement ->
                if (cellElement.localName == "covered-table-cell") {
                    cells += ""
                    cellMetadata += com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata(horizontalMerge = "covered")
                    return@forEach
                }
                if (cellElement.localName != "table-cell") return@forEach
                val cellText = cellElement.textContent.trim()
                val span = cellElement.getAttributeNS(tableNs, "number-columns-spanned").toIntOrNull() ?: 1
                repeat(span) {
                    cells.add(cellText)
                    cellMetadata += com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata(
                        gridSpan = span.takeIf { it > 1 },
                        verticalMerge = cellElement.getAttributeNS(tableNs, "number-rows-spanned").takeIf { it.isNotBlank() },
                        shadingColor = styles[cellElement.getAttributeNS(tableNs, "style-name")]?.color
                    )
                }
            }
            val repeat = rowElement.getAttributeNS(tableNs, "number-rows-repeated").toIntOrNull()?.coerceAtLeast(1) ?: 1
            repeat(repeat) {
                rows += cells.toList()
                metadataRows += cellMetadata.toList()
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
                cellMargins = EdgeInsets(),
                rows = metadataRows
            )
        )
    }

    private fun directChildren(parent: Element, localName: String?): List<Element> =
        (0 until parent.childNodes.length)
            .asSequence()
            .mapNotNull { parent.childNodes.item(it) as? Element }
            .filter { it.namespaceURI == tableNs && (localName == null || it.localName == localName) }
            .toList()
}
