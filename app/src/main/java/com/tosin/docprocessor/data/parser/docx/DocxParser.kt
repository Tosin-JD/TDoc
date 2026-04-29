package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import com.tosin.docprocessor.data.parser.util.DefaultZipExtractor
import com.tosin.docprocessor.data.parser.util.TDocLogger
import com.tosin.docprocessor.data.parser.util.ZipExtractor
import com.tosin.docprocessor.data.parser.validation.DocumentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxParser(
    private val cacheDir: File,
    private val zipExtractor: ZipExtractor = DefaultZipExtractor(),
    private val validator: DocumentValidator = DocumentValidator(),
    private val recoveryStrategy: RecoveryStrategy = GracefulDegradationStrategy(),
    private val numberingParser: DocxNumberingParser = DocxNumberingParser()
) : DocumentParser {

    override suspend fun parse(inputStream: java.io.InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            var currentElement: String? = null
            try {
                TDocLogger.info("Starting DOCX parsing")
                val entries = zipExtractor.extract(inputStream)
                val docxPackage = DocxPackage.from(entries)
                val documentXml = docxPackage.xml("word/document.xml")
                    ?: throw IllegalArgumentException("Not a valid DOCX file: missing word/document.xml")
                val styles = parseStyles(docxPackage.xml("word/styles.xml"))
                numberingParser.parse(docxPackage.xml("word/numbering.xml"))
                val relationships = docxPackage.relationshipsFor("word/document.xml")
                val body = documentXml.documentElement?.firstChild("body")
                    ?: throw IllegalArgumentException("DOCX document is missing body")

                val elements = mutableListOf<DocumentElement>()
                
                val sectPr = body.firstChild("sectPr")
                sectPr?.let { elements += parseSectionHeadersFooters(it, relationships, docxPackage) }

                // Parse footnotes and endnotes
                elements += parseNotes(docxPackage.xml("word/footnotes.xml"), com.tosin.docprocessor.data.parser.internal.models.NoteKind.FOOTNOTE)
                elements += parseNotes(docxPackage.xml("word/endnotes.xml"), com.tosin.docprocessor.data.parser.internal.models.NoteKind.ENDNOTE)
                
                // Parse comments
                elements += parseComments(docxPackage.xml("word/comments.xml"))

                body.children().forEachIndexed { index, child ->
                    currentElement = "BodyElement[$index]: ${child.nodeName}"
                    try {
                        when {
                            child.matches("p") -> elements += parseParagraph(child, styles, relationships, docxPackage)
                            child.matches("tbl") -> parseTable(child)?.let { elements += it }
                        }
                    } catch (error: Exception) {
                        recoveryStrategy.handleFailure(currentElement!!, error) { Unit }
                    }
                }

                val validationResult = validator.validate(elements)
                if (!validationResult.isValid) {
                    TDocLogger.warn("Document validation failed: ${validationResult.errors}")
                }

                TDocLogger.info("Successfully parsed DOCX with ${elements.size} elements")
                Result.success(elements)
            } catch (e: Exception) {
                val context = ParseErrorContext(currentElement = currentElement)
                TDocLogger.error("Failed to parse DOCX", e, mapOf("lastElement" to currentElement))
                Result.failure(ParseException("Failed to parse DOCX: ${e.message}", context, e))
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(outputStream).use { zip ->
                    writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())
                    writeZipEntry(zip, "_rels/.rels", buildRootRelationshipsXml())
                    writeZipEntry(zip, "word/document.xml", buildDocumentXml(content))
                    writeZipEntry(zip, "word/styles.xml", buildStylesXml())
                    writeZipEntry(zip, "word/_rels/document.xml.rels", buildDocumentRelationshipsXml())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                TDocLogger.error("Failed to save DOCX", e)
                Result.failure(e)
            }
        }

    private fun parseParagraph(
        paragraph: Element,
        styles: Map<String, ParagraphStyle>,
        relationships: Map<String, String>,
        docxPackage: DocxPackage
    ): List<DocumentElement> {
        val paragraphStyle = resolveParagraphStyle(paragraph, styles)
        val elements = mutableListOf<DocumentElement>()
        val spans = mutableListOf<TextSpan>()

        fun flushParagraph() {
            if (spans.isEmpty()) return
            val paragraphText = spans.joinToString("") { it.text }
            if (paragraphText.isBlank()) {
                spans.clear()
                return
            }
            val snapshot = spans.toList()
            spans.clear()
            
            val numPr = paragraph.firstChild("pPr")?.firstChild("numPr")
            val listInfo = numberingParser.getListInfo(
                numPr?.firstChild("numId")?.attribute("val"),
                numPr?.firstChild("ilvl")?.attribute("val")
            )

            if (paragraphStyle.headingLevel != null && paragraphText.isNotBlank()) {
                elements += DocumentElement.SectionHeader(
                    text = paragraphText.trim(),
                    level = paragraphStyle.headingLevel
                )
            } else {
                elements += DocumentElement.Paragraph(
                    spans = snapshot,
                    style = paragraphStyle,
                    listInfo = listInfo
                )
            }
        }

        paragraph.children().forEach { child ->
            when {
                child.matches("r") -> {
                    val runContent = parseRun(child)
                    if (runContent.pageBreak) {
                        flushParagraph()
                        elements += DocumentElement.PageBreak()
                    }
                    runContent.image?.let {
                        flushParagraph()
                        parseImage(it, relationships, docxPackage)?.let(elements::add)
                    }
                    spans += runContent.spans
                }
                child.matches("hyperlink") -> {
                    child.children("r").forEach { run ->
                        val runContent = parseRun(run)
                        if (runContent.pageBreak) {
                            flushParagraph()
                            elements += DocumentElement.PageBreak()
                        }
                        runContent.image?.let {
                            flushParagraph()
                            parseImage(it, relationships, docxPackage)?.let(elements::add)
                        }
                        spans += runContent.spans
                    }
                }
            }
        }

        flushParagraph()
        return elements
    }

    private fun parseTable(table: Element): DocumentElement.Table? {
        val tblPr = table.firstChild("tblPr")
        val tableStyleId = tblPr?.firstChild("tblStyle")?.attribute("val")
        val shadingColor = tblPr?.firstChild("shading")?.attribute("fill")
        
        val rows = table.children("tr").map { row ->
            row.children("tc").map { cell ->
                cell.descendants("p")
                    .mapNotNull { paragraph ->
                        val text = paragraph.descendants("t").joinToString("") { it.textContent.orEmpty() }
                            .ifBlank {
                                paragraph.descendants("tab").joinToString("") { "\t" } +
                                    paragraph.descendants("br").joinToString("") { "\n" }
                            }
                        text.takeIf { it.isNotBlank() }
                    }
                    .joinToString("\n")
            }
        }.filter { row -> row.isNotEmpty() }

        return if (rows.isEmpty()) null else DocumentElement.Table(
            rows = rows,
            metadata = com.tosin.docprocessor.data.parser.internal.models.TableMetadata(
                styleId = tableStyleId,
                shadingColor = shadingColor
            )
        )
    }

    private fun parseSectionHeadersFooters(
        sectPr: Element,
        relationships: Map<String, String>,
        docxPackage: DocxPackage
    ): List<DocumentElement> {
        val elements = mutableListOf<DocumentElement>()
        
        fun parseRef(type: String, kind: com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind) {
            sectPr.children(type).forEach { ref ->
                val rId = ref.attribute("id") ?: return@forEach
                val target = relationships[rId] ?: return@forEach
                val xml = docxPackage.xml(target) ?: return@forEach
                val text = xml.documentElement.textContent.trim()
                elements += DocumentElement.HeaderFooter(
                    content = com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent(
                        kind = kind,
                        variant = ref.attribute("type") ?: "default",
                        text = text,
                        paragraphCount = xml.documentElement.descendants("p").size,
                        tableCount = xml.documentElement.descendants("tbl").size
                    )
                )
            }
        }

        parseRef("headerReference", com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind.HEADER)
        parseRef("footerReference", com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind.FOOTER)
        
        return elements
    }

    private fun parseNotes(xml: Document?, kind: com.tosin.docprocessor.data.parser.internal.models.NoteKind): List<DocumentElement> {
        if (xml == null) return emptyList()
        val elements = mutableListOf<DocumentElement>()
        val tagName = when (kind) {
            com.tosin.docprocessor.data.parser.internal.models.NoteKind.FOOTNOTE -> "footnote"
            com.tosin.docprocessor.data.parser.internal.models.NoteKind.ENDNOTE -> "endnote"
        }
        
        xml.documentElement.children(tagName).forEach { note ->
            val id = note.attribute("id") ?: return@forEach
            if (id.toIntOrNull() ?: 0 <= 0) return@forEach
            
            val text = note.descendants("t").joinToString("") { it.textContent.orEmpty() }
            elements += DocumentElement.Note(
                info = com.tosin.docprocessor.data.parser.internal.models.NoteInfo(
                    kind = kind,
                    id = id,
                    text = text
                )
            )
        }
        return elements
    }

    private fun parseComments(xml: Document?): List<DocumentElement> {
        if (xml == null) return emptyList()
        val elements = mutableListOf<DocumentElement>()
        
        xml.documentElement.children("comment").forEach { comment ->
            val id = comment.attribute("id") ?: return@forEach
            val author = comment.attribute("author")
            val text = comment.descendants("t").joinToString("") { it.textContent.orEmpty() }
            
            elements += DocumentElement.Comment(
                info = com.tosin.docprocessor.data.parser.internal.models.CommentInfo(
                    id = id,
                    author = author,
                    text = text
                )
            )
        }
        return elements
    }

    private fun parseRun(run: Element): RunContent {
        val runProperties = run.firstChild("rPr")
        val spans = mutableListOf<TextSpan>()
        val builder = StringBuilder()
        var pageBreak = false

        run.children().forEach { child ->
            when {
                child.matches("t") -> builder.append(child.textContent.orEmpty())
                child.matches("tab") -> builder.append('\t')
                child.matches("br") -> {
                    val breakType = child.attribute("type")
                    if (breakType == "page") {
                        pageBreak = true
                    } else {
                        builder.append('\n')
                    }
                }
            }
        }

        val drawing = run.descendants("drawing").firstOrNull()
        val text = builder.toString()
        if (text.isNotEmpty()) {
            spans += TextSpan(
                text = text,
                isBold = runProperties?.firstChild("b") != null,
                isItalic = runProperties?.firstChild("i") != null,
                isUnderline = runProperties?.firstChild("u") != null,
                isStrikethrough = runProperties?.firstChild("strike") != null || runProperties?.firstChild("dstrike") != null,
                isSuperscript = runProperties?.descendants("vertAlign")?.firstOrNull()?.attribute("val") == "superscript",
                isSubscript = runProperties?.descendants("vertAlign")?.firstOrNull()?.attribute("val") == "subscript",
                fontFamily = runProperties?.descendants("rFonts")?.firstOrNull()?.attribute("ascii"),
                fontSize = runProperties?.descendants("sz")?.firstOrNull()?.attribute("val")?.toIntOrNull()?.div(2),
                color = runProperties?.descendants("color")?.firstOrNull()?.attribute("val") ?: "000000"
            )
        }

        return RunContent(
            spans = spans,
            image = drawing,
            pageBreak = pageBreak
        )
    }

    private fun parseImage(
        drawing: Element,
        relationships: Map<String, String>,
        docxPackage: DocxPackage
    ): DocumentElement.Image? {
        val blip = drawing.descendants("blip").firstOrNull() ?: return null
        val relId = blip.attribute("embed") ?: return null
        val target = relationships[relId] ?: return null
        val bytes = docxPackage.bytes(target) ?: return null
        val fileName = target.substringAfterLast('/')
        val safeName = fileName.ifBlank { "image.bin" }
        val cachedFile = File(cacheDir, "docx_${System.currentTimeMillis()}_$safeName")
        cachedFile.writeBytes(bytes)
        val docPr = drawing.descendants("docPr").firstOrNull()
        return DocumentElement.Image(
            sourceUri = cachedFile.absolutePath,
            altText = docPr?.attribute("descr") ?: docPr?.attribute("title"),
            caption = null
        )
    }

    private fun parseStyles(stylesDocument: Document?): Map<String, ParagraphStyle> {
        if (stylesDocument == null) return emptyMap()
        return stylesDocument.documentElement
            .descendants("style")
            .mapNotNull { style ->
                val styleId = style.attribute("styleId") ?: return@mapNotNull null
                val name = style.firstChild("name")?.attribute("val")
                val paragraphProperties = style.firstChild("pPr")
                val headingLevel = paragraphProperties?.firstChild("outlineLvl")?.attribute("val")?.toIntOrNull()?.plus(1)
                    ?: extractHeadingLevel(styleId, name)

                styleId to ParagraphStyle(
                    styleId = styleId,
                    styleName = name,
                    alignment = parseAlignment(paragraphProperties?.firstChild("jc")?.attribute("val")),
                    indentation = ParagraphIndentation(
                        left = paragraphProperties?.firstChild("ind")?.attribute("left")?.toIntOrNull(),
                        right = paragraphProperties?.firstChild("ind")?.attribute("right")?.toIntOrNull(),
                        firstLine = paragraphProperties?.firstChild("ind")?.attribute("firstLine")?.toIntOrNull(),
                        hanging = paragraphProperties?.firstChild("ind")?.attribute("hanging")?.toIntOrNull()
                    ),
                    spacing = ParagraphSpacing(
                        before = paragraphProperties?.firstChild("spacing")?.attribute("before")?.toIntOrNull(),
                        after = paragraphProperties?.firstChild("spacing")?.attribute("after")?.toIntOrNull(),
                        line = paragraphProperties?.firstChild("spacing")?.attribute("line")?.toIntOrNull()
                    ),
                    outlineLevel = headingLevel?.minus(1),
                    isHeading = headingLevel != null,
                    headingLevel = headingLevel
                )
            }
            .toMap()
    }

    private fun resolveParagraphStyle(
        paragraph: Element,
        styles: Map<String, ParagraphStyle>
    ): ParagraphStyle {
        val properties = paragraph.firstChild("pPr")
        val styleId = properties?.firstChild("pStyle")?.attribute("val")
        val baseStyle = styles[styleId] ?: ParagraphStyle(styleId = styleId)
        val directAlignment = parseAlignment(properties?.firstChild("jc")?.attribute("val"))
        val directHeadingLevel = properties?.firstChild("outlineLvl")?.attribute("val")?.toIntOrNull()?.plus(1)

        return baseStyle.copy(
            alignment = if (directAlignment != ParagraphAlignment.START || baseStyle.alignment == ParagraphAlignment.START) {
                directAlignment
            } else {
                baseStyle.alignment
            },
            headingLevel = directHeadingLevel ?: baseStyle.headingLevel,
            isHeading = directHeadingLevel != null || baseStyle.isHeading
        )
    }

    private fun extractHeadingLevel(styleId: String?, styleName: String?): Int? {
        val probe = listOfNotNull(styleId, styleName)
            .joinToString(" ")
            .lowercase()
        val match = Regex("heading\\s*([1-9])").find(probe) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun parseAlignment(value: String?): ParagraphAlignment =
        when (value?.lowercase()) {
            "right", "end" -> ParagraphAlignment.END
            "center" -> ParagraphAlignment.CENTER
            "both", "justify" -> ParagraphAlignment.JUSTIFIED
            "distribute" -> ParagraphAlignment.DISTRIBUTED
            else -> ParagraphAlignment.START
        }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
          <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelationshipsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildDocumentRelationshipsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
            <w:name w:val="Normal"/>
          </w:style>
          <w:style w:type="paragraph" w:styleId="Heading1">
            <w:name w:val="heading 1"/>
            <w:pPr><w:outlineLvl w:val="0"/></w:pPr>
          </w:style>
          <w:style w:type="paragraph" w:styleId="Heading2">
            <w:name w:val="heading 2"/>
            <w:pPr><w:outlineLvl w:val="1"/></w:pPr>
          </w:style>
          <w:style w:type="paragraph" w:styleId="Heading3">
            <w:name w:val="heading 3"/>
            <w:pPr><w:outlineLvl w:val="2"/></w:pPr>
          </w:style>
        </w:styles>
    """.trimIndent()

    private fun buildDocumentXml(content: List<DocumentElement>): String {
        val body = buildString {
            content.forEach { element ->
                append(renderElement(element))
            }
            append("<w:sectPr/>")
        }

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document
                xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>$body</w:body>
            </w:document>
        """.trimIndent()
    }

    private fun renderElement(element: DocumentElement): String =
        when (element) {
            is DocumentElement.Paragraph -> renderParagraph(element)
            is DocumentElement.SectionHeader -> renderHeader(element)
            is DocumentElement.Table -> renderTable(element)
            is DocumentElement.Image -> renderPlainParagraph(element.caption ?: element.altText ?: "[Image]")
            is DocumentElement.PageBreak -> "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
            is DocumentElement.Section -> renderPlainParagraph(element.properties.toString())
            is DocumentElement.HeaderFooter -> renderPlainParagraph(element.content.text)
            is DocumentElement.Note -> renderPlainParagraph(element.info.text)
            is DocumentElement.Comment -> renderPlainParagraph(element.info.text)
            is DocumentElement.Bookmark -> renderPlainParagraph(element.info.name)
            is DocumentElement.Field -> renderPlainParagraph(element.info.instruction)
            is DocumentElement.Metadata -> renderPlainParagraph("${element.info.title ?: element.info.kind}: ${element.info.summary}")
            is DocumentElement.Drawing -> renderPlainParagraph(element.info.kind)
            is DocumentElement.EmbeddedObject -> renderPlainParagraph(element.info.description ?: element.info.kind)
        }

    private fun renderParagraph(paragraph: DocumentElement.Paragraph): String {
        val paragraphProperties = buildString {
            paragraph.style.headingLevel?.let { level ->
                append("<w:pStyle w:val=\"Heading${level.coerceIn(1, 3)}\"/>")
            }
        }
        val runs = paragraph.spans.joinToString("") { span ->
            val runProperties = buildString {
                if (span.isBold) append("<w:b/>")
                if (span.isItalic) append("<w:i/>")
                if (span.isUnderline) append("<w:u w:val=\"single\"/>")
                if (span.isStrikethrough) append("<w:strike/>")
                span.color.takeIf { it.isNotBlank() }?.let { append("<w:color w:val=\"${escapeXml(it.removePrefix("#"))}\"/>") }
                span.fontSize?.let { append("<w:sz w:val=\"${it * 2}\"/>") }
            }
            val preserved = if (span.text.startsWith(" ") || span.text.endsWith(" ")) " xml:space=\"preserve\"" else ""
            "<w:r>${if (runProperties.isNotBlank()) "<w:rPr>$runProperties</w:rPr>" else ""}<w:t$preserved>${escapeXml(span.text)}</w:t></w:r>"
        }
        return "<w:p>${if (paragraphProperties.isNotBlank()) "<w:pPr>$paragraphProperties</w:pPr>" else ""}$runs</w:p>"
    }

    private fun renderHeader(header: DocumentElement.SectionHeader): String =
        "<w:p><w:pPr><w:pStyle w:val=\"Heading${header.level.coerceIn(1, 3)}\"/></w:pPr><w:r><w:t>${escapeXml(header.text)}</w:t></w:r></w:p>"

    private fun renderTable(table: DocumentElement.Table): String {
        val rows = table.rows.joinToString("") { row ->
            "<w:tr>${row.joinToString("") { cell ->
                "<w:tc><w:p><w:r><w:t>${escapeXml(cell)}</w:t></w:r></w:p></w:tc>"
            }}</w:tr>"
        }
        return "<w:tbl>$rows</w:tbl>"
    }

    private fun renderPlainParagraph(text: String): String =
        "<w:p><w:r><w:t>${escapeXml(text)}</w:t></w:r></w:p>"

    private fun escapeXml(value: String): String = buildString {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                }
            )
        }
    }

    private data class RunContent(
        val spans: List<TextSpan>,
        val image: Element? = null,
        val pageBreak: Boolean = false
    )
}
