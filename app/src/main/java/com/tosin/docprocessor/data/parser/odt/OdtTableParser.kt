package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses ODT tables into [DocumentElement.Table]s with style-resolved shading,
 * borders, cell margins, spans, nested tables, and a [TableCellMetadata] matrix
 * parallel to the text rows.
 */
class OdtTableParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties>,
    private val paragraphParser: OdtParagraphParser
) {

    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val foNs = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"

    fun parseTable(element: Element): DocumentElement.Table {
        val styleName = element.getAttributeNS(tableNs, "style-name").ifEmpty { null }
        val tableStyle = styleName?.let { styles[it] }
        val tableProps = tableStyle?.tableProperties

        val cellMetadata = mutableListOf<List<TableCellMetadata>>()
        val rows = mutableListOf<List<String>>()
        var hasHeader = false

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            when (childElement.localName) {
                "table-header-rows" -> {
                    hasHeader = true
                    parseRows(childElement, rows, cellMetadata)
                }
                "table-row" -> parseRows(childElement, rows, cellMetadata)
                "table-rows" -> parseRows(childElement, rows, cellMetadata)
            }
        }

        val borderSummary = tableProps?.tableBorder?.let { border ->
            listOfNotNull(
                border.color?.let { "top:$it bottom:$it left:$it right:$it" }
            ).takeIf { it.isNotEmpty() }?.joinToString()
        }
        val borderWidth = tableProps?.tableBorder?.width

        return DocumentElement.Table(
            rows = rows,
            hasHeader = hasHeader,
            metadata = TableMetadata(
                styleId = styleName,
                caption = null,
                description = null,
                shadingColor = tableProps?.backgroundColor,
                borderSummary = borderSummary,
                cellMargins = EdgeInsets(
                    top = borderWidth,
                    right = null,
                    bottom = borderWidth,
                    left = null
                ),
                rows = cellMetadata
            )
        )
    }

    private fun parseRows(
        container: Element,
        rows: MutableList<List<String>>,
        cellMetadata: MutableList<List<TableCellMetadata>>
    ) {
        // getElementsByTagNameNS does not match the container element itself,
        // so a direct <table:table-row> passed in as the container is its own
        // row. This must be checked first: searching descendants of a row would
        // otherwise pick up rows nested inside its cells.
        if (container.localName == "table-row") {
            parseRow(container, rows, cellMetadata)
            return
        }
        val rowNodes = container.getElementsByTagNameNS(tableNs, "table-row")
        for (i in 0 until rowNodes.length) {
            parseRow(rowNodes.item(i) as Element, rows, cellMetadata)
        }
    }

    private fun parseRow(
        rowElement: Element,
        rows: MutableList<List<String>>,
        cellMetadata: MutableList<List<TableCellMetadata>>
    ) {
        val rowValues = mutableListOf<String>()
        val rowMeta = mutableListOf<TableCellMetadata>()

        val cellNodes = rowElement.getElementsByTagNameNS(tableNs, "table-cell")
        for (j in 0 until cellNodes.length) {
            val cellElement = cellNodes.item(j) as Element
            val cellText = parseCellText(cellElement)
            val metadata = cellToMetadata(cellElement)

            val repeat = cellElement.getAttributeNS(tableNs, "number-columns-repeated")
                .toIntOrNull()?.coerceAtLeast(1) ?: 1
            repeat(coerceValue(repeat)) {
                rowValues += cellText
                rowMeta += metadata
            }
        }
        rows += rowValues
        cellMetadata += rowMeta
    }

    private fun parseCellText(cellElement: Element): String {
        val nodes = cellElement.getElementsByTagNameNS(textNs, "p")
        val paragraphs = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val p = nodes.item(i) as Element
            val paragraph = paragraphParser.parseParagraph(p)
            paragraphs += buildString {
                paragraph.listLabel?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append(' ')
                }
                append(paragraph.spans.joinToString("") { it.text })
            }
        }
        return paragraphs.joinToString("\n")
    }

    private fun cellToMetadata(cellElement: Element): TableCellMetadata {
        val styleName = cellElement.getAttributeNS(tableNs, "style-name").ifEmpty { null }
        val cellStyle = styleName?.let { styles[it] }
        val cellProps = cellStyle?.tableCellProperties
        val nestedTables = cellElement.getElementsByTagNameNS(tableNs, "table").length

        return TableCellMetadata(
            gridSpan = cellElement.getAttributeNS(tableNs, "number-columns-spanned")
                .toIntOrNull()?.coerceAtLeast(1),
            horizontalMerge = cellElement.getAttributeNS(tableNs, "number-rows-spanned")
                .toIntOrNull()?.takeIf { it > 1 }?.let { "restart" },
            verticalMerge = null,
            shadingColor = cellProps?.backgroundColor
                ?: cellElement.getAttributeNS(foNs, "background-color").ifEmpty { null },
            borderSummary = cellProps?.border?.color?.let {
                "top:$it bottom:$it left:$it right:$it"
            },
            margins = EdgeInsets(
                top = cellProps?.marginTop,
                right = cellProps?.marginRight,
                bottom = cellProps?.marginBottom,
                left = cellProps?.marginLeft
            ),
            nestedTableCount = nestedTables
        )
    }

    private fun coerceValue(value: Int): Int = when {
        value <= 0 -> 1
        value > 100 -> 100
        else -> value
    }
}