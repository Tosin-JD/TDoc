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
import org.apache.poi.xwpf.usermodel.ParagraphAlignment as PoiParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import org.apache.poi.xwpf.usermodel.Document as PoiDocument

class DocxParser(
    private val paragraphParser: DocxParagraphParser = DocxParagraphParser(DocxListParser()),
    private val tableParser: DocxTableParser = DocxTableParser(),
    private val imageParser: DocxImageParser,
    private val structureParser: DocxStructureParser = DocxStructureParser(paragraphParser, tableParser),
    private val fieldParser: DocxFieldParser = DocxFieldParser(),
    private val drawingParser: DocxDrawingParser = DocxDrawingParser(),
    private val pageBreakParser: DocxPageBreakParser = DocxPageBreakParser(),
    private val packageExtractors: List<DocxPackageExtractor> = listOf(
        DocxPackageMetadataExtractor(),
        DocxAdvancedMarkupExtractor(),
        DocxEdgeCaseExtractor()
    )
) : DocumentParser {

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            try {
                val bytes = inputStream.readBytes()
                val doc = XWPFDocument(ByteArrayInputStream(bytes))
                val docxPackage = DocxPackage.from(bytes)
                val elements = mutableListOf<DocumentElement>()
                elements += structureParser.parseDocumentLevelElements(doc)
                packageExtractors.forEach { extractor ->
                    elements += extractor.extract(doc, docxPackage)
                }

                for (bodyElement in doc.bodyElements) {
                    when (bodyElement) {
                        is XWPFParagraph -> {
                            elements += structureParser.parseParagraphMetadata(bodyElement)
                            elements += fieldParser.parse(bodyElement)
                            val images = bodyElement.runs.flatMap { run ->
                                run.embeddedPictures.mapNotNull { pic -> imageParser.parse(pic) }
                            }
                            elements.addAll(images)
                            elements += bodyElement.runs.flatMap { run -> drawingParser.parse(run) }
                            appendParagraphElements(elements, bodyElement)
                            elements += pageBreakParser.parse(bodyElement)
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
                            is DocumentElement.Section -> writeStructureSummary(document, element.properties.toString())
                            is DocumentElement.HeaderFooter -> writeStructureSummary(document, element.content.text)
                            is DocumentElement.Note -> writeStructureSummary(document, element.info.text)
                            is DocumentElement.Comment -> writeStructureSummary(document, element.info.text)
                            is DocumentElement.Bookmark -> writeStructureSummary(document, element.info.name)
                            is DocumentElement.Field -> writeStructureSummary(document, element.info.instruction)
                            is DocumentElement.Metadata -> writeStructureSummary(
                                document,
                                buildString {
                                    append(element.info.title ?: element.info.kind)
                                    append(": ")
                                    append(element.info.summary)
                                }
                            )
                            is DocumentElement.Drawing -> writeStructureSummary(document, element.info.kind)
                            is DocumentElement.EmbeddedObject -> writeStructureSummary(
                                document,
                                element.info.description ?: element.info.kind
                            )
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
        applyParagraphStyle(poiParagraph, paragraph)
        paragraph.listLabel?.takeIf { it.isNotBlank() }?.let { label ->
            poiParagraph.createRun().setText("$label ")
        }
        paragraph.spans.forEach { span ->
            val run = poiParagraph.createRun()
            run.isBold = span.isBold
            run.isItalic = span.isItalic
            run.setUnderline(if (span.isUnderline) UnderlinePatterns.SINGLE else UnderlinePatterns.NONE)
            run.isStrikeThrough = span.isStrikethrough
            run.fontFamily = span.fontFamily
            span.fontSize?.let { run.fontSize = it }
            run.color = span.color.removePrefix("#")
            span.highlightColor?.let { run.setTextHighlightColor(it) }
            span.characterSpacing?.let { run.characterSpacing = it }
            span.language?.let { run.lang = it }
            run.setText(span.text)
        }
    }

    private fun writeHeader(document: XWPFDocument, header: DocumentElement.SectionHeader) {
        val paragraph = document.createParagraph()
        val run = paragraph.createRun()
        run.isBold = true
        paragraph.style = "Heading${header.level.coerceIn(1, 9)}"
        run.fontSize = when (header.level) {
            1 -> 20
            2 -> 16
            else -> 14
        }
        run.setText(header.text)
    }

    private fun writeStructureSummary(document: XWPFDocument, text: String) {
        if (text.isBlank()) return
        document.createParagraph().createRun().setText(text)
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

    private fun appendParagraphElements(
        elements: MutableList<DocumentElement>,
        poiParagraph: XWPFParagraph
    ) {
        val parsedParagraph = paragraphParser.parse(poiParagraph)
        val text = parsedParagraph.spans.joinToString(separator = "") { it.text }.trim()
        if (text.isEmpty() && parsedParagraph.listLabel == null) {
            return
        }

        parsedParagraph.style.headingLevel?.let { level ->
            if (text.isNotEmpty()) {
                elements.add(DocumentElement.SectionHeader(text = text, level = level))
                return
            }
        }

        elements.add(parsedParagraph)
    }

    private fun applyParagraphStyle(
        poiParagraph: XWPFParagraph,
        paragraph: DocumentElement.Paragraph
    ) {
        val style = paragraph.style
        style.styleId?.takeIf { it.isNotBlank() }?.let { poiParagraph.style = it }
        poiParagraph.alignment = when (style.alignment) {
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.END -> {
                PoiParagraphAlignment.RIGHT
            }
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.CENTER -> {
                PoiParagraphAlignment.CENTER
            }
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.JUSTIFIED -> {
                PoiParagraphAlignment.BOTH
            }
            com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.DISTRIBUTED -> {
                PoiParagraphAlignment.DISTRIBUTE
            }
            else -> PoiParagraphAlignment.LEFT
        }
        style.indentation.left?.let { poiParagraph.indentationLeft = it }
        style.indentation.right?.let { poiParagraph.indentationRight = it }
        style.indentation.firstLine?.let { poiParagraph.indentationFirstLine = it }
        style.indentation.hanging?.let { poiParagraph.indentationHanging = it }
        style.spacing.before?.let { poiParagraph.spacingBefore = it }
        style.spacing.after?.let { poiParagraph.spacingAfter = it }
        style.spacing.line?.let { poiParagraph.spacingBetween = it / 240.0 }
    }
}
