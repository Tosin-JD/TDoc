package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.LegacyElementAdapter
import com.tosin.docprocessor.data.parser.common.ListBlockGrouper
import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.PackageInventoryBuilder
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.rAttr
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.mapper.DocxBlockMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxMapContext
import com.tosin.docprocessor.data.parser.docx.mapper.DocxMetadataMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxSectionMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxTableMapper
import com.tosin.docprocessor.data.parser.docx.opc.OpcPackage
import com.tosin.docprocessor.data.parser.docx.opc.ContentTypes
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.docx.resolve.DocxNumberingResolver
import com.tosin.docprocessor.data.parser.docx.resolve.DocxSectionProperties
import com.tosin.docprocessor.data.parser.docx.resolve.DocxStyleResolver
import com.tosin.docprocessor.data.parser.docx.resolve.DocxThemeResolver
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.NoteKind
import com.tosin.docprocessor.domain.document.model.OpaquePackagePart
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.model.DocumentElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Rich WordprocessingML parser. Produces a [ParsedDocument] plus the temporary
 * legacy [DocumentElement] projection. Never writes to disk while parsing.
 */
class DocxDocumentParser(
    private val entries: Map<String, ByteArray>,
    private val cacheDir: File? = null,
    private val diagnostics: DiagnosticsCollector = DiagnosticsCollector(),
    private val resources: ResourceCollector = ResourceCollector()
) : DocumentParser {

    private val legacySaver: DocxParser? = cacheDir?.let { DocxParser(it) }

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        parseWithReport(inputStream).map { it.content }

    override suspend fun parseWithReport(inputStream: InputStream): Result<ParseResult> =
        withContext(Dispatchers.IO) {
            runCatching { parseEntries(entries) }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> {
        val saver = legacySaver ?: return Result.failure(
            IllegalStateException("Export disabled: rich parser is read-only. Use an export-basic-copy action.")
        )
        return saver.save(outputStream, content)
    }

    fun parseEntries(entries: Map<String, ByteArray>): ParseResult {
        val encryptedContentType = ContentTypes.parse(entries["[Content_Types].xml"])
            .let { types -> (types.defaults.values + types.overrides.values) }
            .any { it.contains("encryptedPackage", ignoreCase = true) }
        if (entries.keys.any { it.equals("EncryptedPackage", true) || it.equals("EncryptionInfo", true) } ||
            encryptedContentType
        ) {
            throw ParseException(
                "Encrypted DOCX documents are not supported",
                ParseErrorContext(fileName = "EncryptedPackage")
            )
        }
        val opc = OpcPackage.parse(entries, diagnostics)
        val mainPart = opc.mainDocumentPart()
            ?: throw IllegalArgumentException("Not a valid DOCX: no main document part found")
        val document = opc.document(mainPart)
            ?: throw IllegalArgumentException("Unable to parse main document part: $mainPart")
        val body = document.documentElement?.firstNs(Ns.W, "body")
            ?: throw IllegalArgumentException("DOCX document is missing w:body")

        val styleResolver = DocxStyleResolver.parse(
            (opc.partByRelationshipType(mainPart, "styles") ?: "word/styles.xml").let(opc::document)
        )
        val numbering = DocxNumberingResolver.parse(
            (opc.partByRelationshipType(mainPart, "numbering") ?: "word/numbering.xml").let(opc::document)
        ).withStyleLinks(styleResolver.styleLinks())

        val theme = DocxThemeResolver.parse(
            (opc.partByRelationshipType(mainPart, "theme") ?: "word/theme/theme1.xml").let(opc::document)
        )
        val sectionMapper = DocxSectionMapper(opc, mainPart, styleResolver, numbering, resources, diagnostics)
        val settings = sectionMapper.parseSettings(sectionMapper.settingsDocument())

        val partDiagnostics = DiagnosticsCollector(mainPart)
        val context = DocxMapContext(
            mainPart, opc, styleResolver, numbering, resources, partDiagnostics,
            theme = theme, evenAndOddHeaders = settings.evenAndOddHeaders
        )
        val blockMapper = DocxBlockMapper(context).also { it.attach(DocxTableMapper(context, it)) }

        val bodyBlocks = blockMapper.parseBlocks(body)
        val grouped = ListBlockGrouper.group(bodyBlocks) { numId ->
            numbering.asListStyle(styleId = null, numId = numId)
        }
        val background = document.documentElement?.wFirst("background")
        val backgroundColor = background?.wAttr("fill")?.takeIf { it.isNotBlank() }
        val backgroundRelationshipId = background?.rAttr("id")
            ?: background?.descendantsNs(Ns.VML, "fill")?.firstOrNull()?.rAttr("id")
        val backgroundImage = backgroundRelationshipId?.let { relationshipId ->
            opc.relationshipsFor(mainPart)[relationshipId]
        }?.let { relationship ->
            if (relationship.isExternal) {
                resources.add(relationship.target, isExternal = true)
            } else {
                resources.add(relationship.target, bytes = opc.part(relationship.target))
            }
        }
        // The document-level sectPr closes the final section in flow.
        val blocks = body.wFirst("sectPr")?.let { sectPr ->
            grouped + BlockNode.SectionBreak(
                style = DocxSectionProperties.parse(
                    sectPr, blockMapper.sectionBreakCount + 1, settings.evenAndOddHeaders
                ),
                anchor = SourceAnchor(mainPart, "w:sectPr")
            )
        } ?: grouped
        val blocksWithBackground = blocks.map { block ->
            if (block is BlockNode.SectionBreak && (backgroundColor != null || backgroundImage != null)) {
                block.copy(
                    style = block.style.copy(
                        page = block.style.page.copy(
                            background = backgroundColor ?: block.style.page.background,
                            backgroundImageResourceId = backgroundImage ?: block.style.page.backgroundImageResourceId
                        )
                    )
                )
            } else block
        }

        val sections = sectionMapper.collectSections(body, settings).map { section ->
            section.copy(
                page = section.page.copy(
                    background = backgroundColor ?: section.page.background,
                    backgroundImageResourceId = backgroundImage ?: section.page.backgroundImageResourceId
                )
            )
        }
        val headerFooters = sectionMapper.parseHeaderFooters(body, settings)
        val comments = sectionMapper.parseComments(opc.document("word/comments.xml"))
        val notes = sectionMapper.parseNotes(
            opc.document("word/footnotes.xml"), NoteKind.FOOTNOTE, "word/footnotes.xml"
        ) + sectionMapper.parseNotes(
            opc.document("word/endnotes.xml"), NoteKind.ENDNOTE, "word/endnotes.xml"
        )

        val metadata = DocxMetadataMapper(opc).parse()
        diagnostics.merge(partDiagnostics)

        val parsed = ParsedDocument(
            blocks = blocksWithBackground,
            metadata = metadata,
            styles = styleResolver.catalogue(),
            resources = resources.resources(),
            sections = sections,
            headerFooters = headerFooters,
            comments = comments,
            notes = notes,
            settings = settings,
            sourceFormat = SourceFormat.DOCX,
            diagnostics = diagnostics.diagnostics,
            compatibility = diagnostics.compatibilityLevel(),
            opaqueParts = preservedParts(opc),
            outline = ParsedDocument.outlineOf(blocksWithBackground)
        )

        return ParseResult(
            document = parsed,
            content = LegacyElementAdapter().adapt(parsed),
            diagnostics = diagnostics.diagnostics,
            compatibility = parsed.compatibility,
            packageInventory = PackageInventoryBuilder.fromEntries(entries, recognizedPaths(opc)),
            preservedParts = parsed.opaqueParts
        )
    }

    private fun recognizedPaths(opc: OpcPackage): Set<String> = entries.keys.filter { path ->
        path == "[Content_Types].xml" || path.endsWith(".rels") || opc.contentTypes.contentTypeFor(path) != null
    }.toSet()

    private fun preservedParts(opc: OpcPackage): List<OpaquePackagePart> = entries
        .filterKeys {
            it.startsWith("word/glossary/") ||
                (opc.contentTypes.contentTypeFor(it) == null && it != "[Content_Types].xml" && !it.endsWith(".rels"))
        }
        .map { (path, bytes) ->
            OpaquePackagePart(
                path = path,
                mimeType = null,
                bytes = bytes,
                role = if (path.startsWith("word/glossary/")) "glossary" else "unrecognised"
            )
        }

    companion object {
        fun fromBytes(bytes: ByteArray, cacheDir: File? = null): DocxDocumentParser {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
            return DocxDocumentParser(entries, cacheDir)
        }

        fun read(inputStream: InputStream, cacheDir: File? = null): DocxDocumentParser =
            fromBytes(inputStream.readBytes(), cacheDir)
    }
}
