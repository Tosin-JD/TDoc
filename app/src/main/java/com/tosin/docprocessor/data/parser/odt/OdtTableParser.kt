package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets

class OdtTableParser {

    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"

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
                repeat(span) {
                    cells.add(cellText)
                }
            }
            rows.add(cells)
        }

        return DocumentElement.Table(
            rows = rows,
            hasHeader = false,
            metadata = TableMetadata(
                styleId = "Default",
                caption = null,
                description = null,
                shadingColor = null,
                borderSummary = null,
                cellMargins = EdgeInsets()
            )
        )
    }
}
