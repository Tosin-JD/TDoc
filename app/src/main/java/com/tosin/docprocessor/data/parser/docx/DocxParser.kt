package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.BookmarkBoundary
import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterReference
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.LineSpacingRule
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
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
                val relationshipDetails = docxPackage.relationshipDetailsFor("word/document.xml")
                val body = documentXml.documentElement?.firstChild("body")
                    ?: throw IllegalArgumentException("DOCX document is missing body")

                val elements = mutableListOf<DocumentElement>()
                parseMetadata(docxPackage)?.let(elements::add)
                
                // Parse footnotes and endnotes
                elements += parseNotes(docxPackage.xml("word/footnotes.xml"), com.tosin.docprocessor.data.parser.internal.models.NoteKind.FOOTNOTE)
                elements += parseNotes(docxPackage.xml("word/endnotes.xml"), com.tosin.docprocessor.data.parser.internal.models.NoteKind.ENDNOTE)
                
                // Parse comments
                elements += parseComments(docxPackage.xml("word/comments.xml"))

                var sectionIndex = 0
                body.children().forEachIndexed { index, child ->
                    currentElement = "BodyElement[$index]: ${child.nodeName}"
                    try {
                        when {
                            child.matches("p") -> {
                                elements += parseParagraph(child, styles, relationships, relationshipDetails, docxPackage)
                                child.firstChild("pPr")?.firstChild("sectPr")?.let { sectionProperties ->
                                    sectionIndex += 1
                                    elements += DocumentElement.Section(properties = parseSectionProperties(sectionProperties, sectionIndex))
                                    elements += parseSectionHeadersFooters(sectionProperties, relationships, docxPackage)
                                }
                            }
                            child.matches("tbl") -> parseTable(child)?.let { elements += it }
                            child.matches("sectPr") -> {
                                sectionIndex += 1
                                elements += DocumentElement.Section(properties = parseSectionProperties(child, sectionIndex))
                                elements += parseSectionHeadersFooters(child, relationships, docxPackage)
                            }
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
        relationshipDetails: Map<String, DocxPackage.Relationship>,
        docxPackage: DocxPackage
    ): List<DocumentElement> {
        val paragraphStyle = resolveParagraphStyle(paragraph, styles)
        val elements = mutableListOf<DocumentElement>()
        val spans = mutableListOf<TextSpan>()

        fun flushParagraph() {
            if (spans.isEmpty()) return
            val paragraphText = spans.joinToString("") { it.text }
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
                    val link = resolveHyperlink(child, relationshipDetails)
                    child.children("r").forEach { run ->
                        val runContent = parseRun(run, link)
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
                child.matches("bookmarkStart") -> {
                    child.attribute("name")?.takeIf { it.isNotBlank() }?.let { name ->
                        elements += DocumentElement.Bookmark(
                            info = BookmarkInfo(
                                id = child.attribute("id").orEmpty(),
                                name = name,
                                boundary = BookmarkBoundary.START,
                                source = "word/document.xml"
                            )
                        )
                    }
                }
                child.matches("bookmarkEnd") -> {
                    elements += DocumentElement.Bookmark(
                        info = BookmarkInfo(
                            id = child.attribute("id").orEmpty(),
                            name = child.attribute("name").orEmpty(),
                            boundary = BookmarkBoundary.END,
                            source = "word/document.xml"
                        )
                    )
                }
                child.matches("fldSimple") -> {
                    val instruction = child.attribute("instr").orEmpty()
                    elements += DocumentElement.Field(
                        info = FieldInfo(
                            type = instruction.substringBefore(' ').ifBlank { "simple" },
                            instruction = instruction,
                            value = child.descendants("t").joinToString("") { it.textContent.orEmpty() },
                            isSimpleField = true,
                            source = "word/document.xml"
                        )
                    )
                }
            }
        }

        flushParagraph()
        // Empty paragraphs are layout-significant in Word documents. Do not
        // turn a blank line into missing content simply because it has no run.
        if (elements.isEmpty() && spans.isEmpty()) {
            elements += DocumentElement.Paragraph(spans = emptyList(), style = paragraphStyle)
        }
        return elements
    }

    private fun parseMetadata(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val core = docxPackage.xml("docProps/core.xml")?.documentElement
        val app = docxPackage.xml("docProps/app.xml")?.documentElement
        val custom = docxPackage.xml("docProps/custom.xml")?.documentElement
        if (core == null && app == null && custom == null) return null

        fun value(root: Element?, name: String): String? = root
            ?.descendants(name)
            ?.firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val attributes = linkedMapOf<String, String>()
        listOf(
            "subject", "creator", "lastModifiedBy", "created", "modified", "keywords", "category", "contentStatus"
        ).forEach { key -> value(core, key)?.let { attributes[key] = it } }
        listOf("Application", "Pages", "Words", "Characters", "Paragraphs", "Company", "Template").forEach { key ->
            value(app, key)?.let { attributes[key] = it }
        }
        custom?.children("property")?.forEach { property ->
            val name = property.attribute("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            property.textContent?.trim()?.takeIf { it.isNotEmpty() }?.let { attributes["custom.$name"] = it }
        }
        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = "DOCX Metadata",
                title = value(core, "title"),
                summary = value(core, "description") ?: "DOCX document processed via TDoc",
                attributes = attributes,
                source = "docProps"
            )
        )
    }

    private fun resolveHyperlink(
        hyperlink: Element,
        relationships: Map<String, DocxPackage.Relationship>
    ): HyperlinkInfo? {
        val relationship = hyperlink.attribute("id")?.let(relationships::get)
        val anchor = hyperlink.attribute("anchor")?.takeIf { it.isNotBlank() }
        val address = relationship?.target?.takeIf { relationship.isExternal }
        if (address == null && anchor == null) return null
        return HyperlinkInfo(
            address = address,
            anchor = anchor,
            tooltip = hyperlink.attribute("tooltip")?.takeIf { it.isNotBlank() },
            docLocation = hyperlink.attribute("docLocation")?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseTable(table: Element): DocumentElement.Table? {
        val tblPr = table.firstChild("tblPr")
        val tableStyleId = tblPr?.firstChild("tblStyle")?.attribute("val")
        val shadingColor = tblPr?.firstChild("shading")?.attribute("fill")
        
        val rowMetadata = mutableListOf<List<TableCellMetadata>>()
        val rows = table.children("tr").map { row ->
            val cellsMetadata = mutableListOf<TableCellMetadata>()
            val cells = row.children("tc").map { cell ->
                val cellProperties = cell.firstChild("tcPr")
                cellsMetadata += TableCellMetadata(
                    gridSpan = cellProperties?.firstChild("gridSpan")?.attribute("val")?.toIntOrNull(),
                    horizontalMerge = cellProperties?.firstChild("hMerge")?.attribute("val"),
                    verticalMerge = cellProperties?.firstChild("vMerge")?.attribute("val"),
                    shadingColor = cellProperties?.firstChild("shd")?.attribute("fill"),
                    nestedTableCount = cell.children("tbl").size
                )
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
            rowMetadata += cellsMetadata
            cells
        }.filter { row -> row.isNotEmpty() }

        return if (rows.isEmpty()) null else DocumentElement.Table(
            rows = rows,
            hasHeader = table.children("tr").firstOrNull()
                ?.firstChild("trPr")?.firstChild("tblHeader") != null,
            metadata = TableMetadata(
                styleId = tableStyleId,
                shadingColor = shadingColor,
                rows = rowMetadata
            )
        )
    }

    private fun parseSectionProperties(sectPr: Element, sectionIndex: Int): SectionProperties {
        val pageSize = sectPr.firstChild("pgSz")
        val pageMargins = sectPr.firstChild("pgMar")
        val columns = sectPr.firstChild("cols")
        fun references(tag: String): List<HeaderFooterReference> = sectPr.children(tag).map {
            HeaderFooterReference(
                relationshipId = it.attribute("id"),
                type = it.attribute("type")
            )
        }
        return SectionProperties(
            sectionIndex = sectionIndex,
            source = "word/document.xml",
            type = sectPr.firstChild("type")?.attribute("val"),
            pageWidth = pageSize?.attribute("w")?.toIntOrNull(),
            pageHeight = pageSize?.attribute("h")?.toIntOrNull(),
            margins = EdgeInsets(
                top = pageMargins?.attribute("top")?.toIntOrNull(),
                right = pageMargins?.attribute("right")?.toIntOrNull(),
                bottom = pageMargins?.attribute("bottom")?.toIntOrNull(),
                left = pageMargins?.attribute("left")?.toIntOrNull()
            ),
            columnCount = columns?.attribute("num")?.toIntOrNull(),
            pageNumberStart = sectPr.firstChild("pgNumType")?.attribute("start")?.toIntOrNull(),
            headerReferences = references("headerReference"),
            footerReferences = references("footerReference")
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
                    content = HeaderFooterContent(
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
                info = NoteInfo(
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
                info = CommentInfo(
                    id = id,
                    author = author,
                    text = text
                )
            )
        }
        return elements
    }

    private fun parseRun(run: Element, hyperlink: HyperlinkInfo? = null): RunContent {
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
                fontSize = runProperties?.descendants("sz")?.firstOrNull()?.attribute("val")?.toIntOrNull()?.div(2)?.toFloat(),
                color = runProperties?.descendants("color")?.firstOrNull()?.attribute("val") ?: "000000",
                highlightColor = runProperties?.firstChild("highlight")?.attribute("val"),
                language = runProperties?.firstChild("lang")?.attribute("val"),
                characterSpacing = runProperties?.firstChild("spacing")?.attribute("val")?.toIntOrNull(),
                hyperlink = hyperlink
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
        val suffix = safeName.substringAfterLast('.', "bin").let { ".${it.take(16)}" }
        val cachedFile = File.createTempFile("docx_", suffix, cacheDir)
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
                        left = paragraphProperties?.firstChild("ind")?.attribute("left")?.toIntOrNull()?.takeIf { it > 0 },
                        right = paragraphProperties?.firstChild("ind")?.attribute("right")?.toIntOrNull()?.takeIf { it > 0 },
                        firstLine = paragraphProperties?.firstChild("ind")?.attribute("firstLine")?.toIntOrNull()?.takeIf { it > 0 },
                        hanging = paragraphProperties?.firstChild("ind")?.attribute("hanging")?.toIntOrNull()?.takeIf { it > 0 }
                    ),
                    spacing = ParagraphSpacing(
                        before = paragraphProperties?.firstChild("spacing")?.attribute("before")?.toIntOrNull()?.takeIf { it > 0 },
                        after = paragraphProperties?.firstChild("spacing")?.attribute("after")?.toIntOrNull()?.takeIf { it > 0 },
                        line = paragraphProperties?.firstChild("spacing")?.attribute("line")?.toIntOrNull()?.takeIf { it > 0 },
                        lineRule = parseLineSpacingRule(paragraphProperties?.firstChild("spacing")?.attribute("lineRule"))
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
        val directIndentation = properties?.firstChild("ind")
        val directSpacing = properties?.firstChild("spacing")

        return baseStyle.copy(
            alignment = if (directAlignment != ParagraphAlignment.START || baseStyle.alignment == ParagraphAlignment.START) {
                directAlignment
            } else {
                baseStyle.alignment
            },
            indentation = baseStyle.indentation.copy(
                left = directIndentation?.attribute("left")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: directIndentation?.attribute("start")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.indentation.left,
                right = directIndentation?.attribute("right")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: directIndentation?.attribute("end")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.indentation.right,
                firstLine = directIndentation?.attribute("firstLine")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.indentation.firstLine,
                hanging = directIndentation?.attribute("hanging")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.indentation.hanging
            ),
            spacing = baseStyle.spacing.copy(
                before = directSpacing?.attribute("before")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.spacing.before,
                after = directSpacing?.attribute("after")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.spacing.after,
                line = directSpacing?.attribute("line")?.toIntOrNull()?.takeIf { it > 0 }
                    ?: baseStyle.spacing.line,
                lineRule = parseLineSpacingRule(directSpacing?.attribute("lineRule"))
                    ?: baseStyle.spacing.lineRule
            ),
            headingLevel = directHeadingLevel ?: baseStyle.headingLevel,
            isHeading = directHeadingLevel != null || baseStyle.isHeading
        )
    }

    private fun parseLineSpacingRule(value: String?): LineSpacingRule? = when (value?.lowercase()) {
        "auto" -> LineSpacingRule.AUTO
        "exact" -> LineSpacingRule.EXACT
        "atleast" -> LineSpacingRule.AT_LEAST
        else -> null
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
