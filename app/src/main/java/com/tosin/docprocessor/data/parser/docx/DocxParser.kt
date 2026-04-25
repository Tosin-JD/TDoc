package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import org.apache.poi.xwpf.usermodel.Document as PoiDocument

class DocxParser(
    // We pass the ListParser into the ParagraphParser
    private val paragraphParser: DocxParagraphParser = DocxParagraphParser(DocxListParser()),
    private val tableParser: DocxTableParser = DocxTableParser(),
    private val imageParser: DocxImageParser
) : DocumentParser {

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            try {
                val doc = XWPFDocument(inputStream)
                val elements = mutableListOf<DocumentElement>()

                for (bodyElement in doc.bodyElements) {
                    when (bodyElement) {
                        is XWPFParagraph -> {
                            // Extract images found within this paragraph's runs
                            val images = bodyElement.runs.flatMap { run ->
                                run.embeddedPictures.mapNotNull { pic -> imageParser.parse(pic) }
                            }

                            elements.addAll(images)

                            // The paragraphParser now handles checking for list labels automatically
                            elements.add(paragraphParser.parse(bodyElement))
                        }
                        is XWPFTable -> {
                            elements.add(tableParser.parse(bodyElement))
                        }
                    }
                }

                Result.success(elements)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                XWPFDocument().use { document ->
                    content.forEach { element ->
                        when (element) {
                            is DocumentElement.Paragraph -> writeParagraph(document, element)
                            is DocumentElement.SectionHeader -> writeHeader(document, element)
                            is DocumentElement.Table -> writeTable(document, element)
                            is DocumentElement.Image -> writeImage(document, element)
                            DocumentElement.PageBreak -> document.createParagraph().createRun().addBreak(BreakType.PAGE)
                        }
                    }
                    document.write(outputStream)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun writeParagraph(document: XWPFDocument, paragraph: DocumentElement.Paragraph) {
        val poiParagraph = document.createParagraph()
        paragraph.listLabel?.takeIf { it.isNotBlank() }?.let { label ->
            poiParagraph.createRun().setText("$label ")
        }
        paragraph.spans.forEach { span ->
            val run = poiParagraph.createRun()
            run.isBold = span.isBold
            run.isItalic = span.isItalic
            run.color = span.color.removePrefix("#")
            run.setText(span.text)
        }
    }

    private fun writeHeader(document: XWPFDocument, header: DocumentElement.SectionHeader) {
        val paragraph = document.createParagraph()
        val run = paragraph.createRun()
        run.isBold = true
        run.fontSize = when (header.level) {
            1 -> 20
            2 -> 16
            else -> 14
        }
        run.setText(header.text)
    }

    private fun writeTable(document: XWPFDocument, table: DocumentElement.Table) {
        if (table.rows.isEmpty()) return

        val poiTable = document.createTable(table.rows.size, table.rows.firstOrNull()?.size ?: 1)
        table.rows.forEachIndexed { rowIndex, row ->
            val tableRow = poiTable.getRow(rowIndex)
            row.forEachIndexed { cellIndex, cellText ->
                tableRow.getCell(cellIndex).text = cellText
            }
        }
    }

    private fun writeImage(document: XWPFDocument, image: DocumentElement.Image) {
        val imageFile = java.io.File(image.sourceUri)
        if (!imageFile.exists()) {
            image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                document.createParagraph().createRun().setText(caption)
            }
            return
        }

        FileInputStream(imageFile).use { imageStream ->
            val pictureType = when (imageFile.extension.lowercase()) {
                "png" -> PoiDocument.PICTURE_TYPE_PNG
                "jpg", "jpeg" -> PoiDocument.PICTURE_TYPE_JPEG
                "gif" -> PoiDocument.PICTURE_TYPE_GIF
                "bmp" -> PoiDocument.PICTURE_TYPE_BMP
                "webp" -> PoiDocument.PICTURE_TYPE_PNG
                else -> PoiDocument.PICTURE_TYPE_PNG
            }
            val paragraph = document.createParagraph()
            val run = paragraph.createRun()
            run.addPicture(
                imageStream,
                pictureType,
                imageFile.name,
                Units.toEMU(400.0),
                Units.toEMU(300.0)
            )
        }

        image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            document.createParagraph().createRun().setText(caption)
        }
    }
}
