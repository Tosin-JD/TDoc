package com.tosin.docprocessor.data.parser

import android.graphics.BitmapFactory
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.data.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.InputStream
import java.io.OutputStream

class DocxParser : DocumentParser {
    override fun parse(inputStream: InputStream): List<DocumentElement> {
        val elements = mutableListOf<DocumentElement>()
        try {
            val doc = XWPFDocument(inputStream)

            // 1. Handle Header
            doc.headerList.firstOrNull()?.text?.let {
                if (it.isNotBlank()) elements.add(DocumentElement.Header(it))
            }

            // 2. Sequential Parsing of Body Elements
            doc.bodyElements.forEach { element ->
                when (element) {
                    is XWPFParagraph -> {
                        // Check for images in runs first
                        element.runs.forEach { run ->
                            run.embeddedPictures.forEach { pic ->
                                val data = pic.pictureData.data
                                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                                elements.add(DocumentElement.Image(bitmap, pic.description ?: "Image"))
                            }
                        }
                        
                        val annotatedString = parseParagraph(element)
                        if (annotatedString.isNotEmpty()) {
                            elements.add(DocumentElement.Paragraph(annotatedString))
                        }
                    }
                    is XWPFTable -> {
                        val rows = element.rows.map { row ->
                            row.tableCells.map { it.text }
                        }
                        elements.add(DocumentElement.Table(rows))
                    }
                }
            }

            // 3. Handle Footer
            doc.footerList.firstOrNull()?.text?.let {
                if (it.isNotBlank()) elements.add(DocumentElement.Footer(it))
            }

        } catch (e: Exception) {
            elements.add(DocumentElement.Paragraph(AnnotatedString("Error parsing Word document: ${e.message}")))
        }
        return elements
    }

    private fun parseParagraph(paragraph: XWPFParagraph): AnnotatedString {
        val builder = AnnotatedString.Builder()
        paragraph.runs.forEach { run ->
            val start = builder.length
            builder.append(run.text() ?: "")
            val end = builder.length

            if (run.isBold) {
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            if (run.isItalic) {
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
            if (run.fontSize > 0) {
                builder.addStyle(SpanStyle(fontSize = run.fontSize.sp), start, end)
            }
        }
        return builder.toAnnotatedString()
    }

    override fun save(outputStream: OutputStream, content: List<DocumentElement>) {
        val doc = XWPFDocument()
        
        content.forEach { element ->
            when (element) {
                is DocumentElement.Paragraph -> {
                    saveParagraph(doc.createParagraph(), element.content)
                }
                is DocumentElement.Table -> {
                    val table = doc.createTable(element.rows.size, element.rows.firstOrNull()?.size ?: 0)
                    element.rows.forEachIndexed { rowIndex, rowData ->
                        val row = table.getRow(rowIndex)
                        rowData.forEachIndexed { colIndex, cellData ->
                            row.getCell(colIndex).text = cellData
                        }
                    }
                }
                // Headers, Footers, and Images are complex to save back in this simplified model
                // For now, we focus on preserving the structure of text and tables
                else -> {}
            }
        }
        
        doc.write(outputStream)
    }

    private fun saveParagraph(p: XWPFParagraph, content: AnnotatedString) {
        // Re-use the previous span-to-run logic
        val spanStyles = content.spanStyles
        if (spanStyles.isEmpty()) {
            p.createRun().setText(content.text)
        } else {
            val boundaries = mutableSetOf(0, content.length)
            spanStyles.forEach {
                boundaries.add(it.start)
                boundaries.add(it.end)
            }
            val sortedBoundaries = boundaries.sorted()
            for (i in 0 until sortedBoundaries.size - 1) {
                val start = sortedBoundaries[i]
                val end = sortedBoundaries[i + 1]
                if (start == end) continue

                val runText = content.text.substring(start, end)
                val run = p.createRun()
                run.setText(runText)

                val midpoint = start + (end - start) / 2
                val activeSpans = spanStyles.filter { it.start <= midpoint && it.end > midpoint }
                activeSpans.forEach { span ->
                    if (span.item.fontWeight == FontWeight.Bold) run.isBold = true
                    if (span.item.fontStyle == FontStyle.Italic) run.isItalic = true
                    span.item.fontSize.takeIf { !it.isUnspecified }?.let {
                        if (it.isSp) run.fontSize = it.value.toInt()
                    }
                }
            }
        }
    }
}
