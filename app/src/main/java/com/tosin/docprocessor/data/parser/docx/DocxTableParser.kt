package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFTable

class DocxTableParser(
    private val paragraphParser: DocxParagraphParser = DocxParagraphParser()
) {

    fun parse(poiTable: XWPFTable): DocumentElement.Table {
        val tableRows = poiTable.rows.map { poiRow ->
            poiRow.tableCells.map { poiCell ->
                // A cell is essentially a mini-document.
                // We extract all paragraphs and join their text.
                val cellText = poiCell.paragraphs.joinToString("\n") { poiParagraph ->
                    val paragraph = paragraphParser.parse(poiParagraph)
                    // Convert the Paragraph model back to plain text for the Table model
                    paragraph.spans.joinToString("") { it.text }
                }

                // You can expand TableCell later to include styling
                cellText
            }
        }

        return DocumentElement.Table(rows = tableRows)
    }
}