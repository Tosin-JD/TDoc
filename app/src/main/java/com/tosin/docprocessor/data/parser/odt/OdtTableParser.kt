package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import org.w3c.dom.Element

import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets

class OdtTableParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties> = emptyMap()
) {

    private val tableNs = OdtNamespaces.TABLE

    fun parseTable(element: Element): DocumentElement.Table {
        val rows = mutableListOf<List<String>>()
        
        val rowNodes = element.getElementsByTagNameNS(tableNs, "table-row")
        for (i in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(i) as Element
            val cells = mutableListOf<String>()
            
            val cellNodes = rowElement.getElementsByTagNameNS(tableNs, "table-cell")
            for (j in 0 until cellNodes.length) {
                val cellElement = cellNodes.item(j) as Element
                val cellText = cellElement.textContent.trim()
                
                // Handle column span
                val span = cellElement.getAttributeNS(tableNs, "number-columns-spanned").toIntOrNull() ?: 1
                val cellStyleName = cellElement.getAttributeNS(tableNs, "style-name")
                val cellStyle = styles[cellStyleName]
                
                repeat(span) {
                    cells.add(cellText)
                }
            }
            rows.add(cells)
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
                cellMargins = EdgeInsets()
            )
        )
    }
}
