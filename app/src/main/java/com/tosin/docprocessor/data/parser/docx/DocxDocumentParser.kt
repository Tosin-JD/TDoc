package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.LegacyElementAdapter
import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.PackageInventoryBuilder
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.docx.mapper.DocxBlockMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxMapContext
import com.tosin.docprocessor.data.parser.docx.mapper.DocxMetadataMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxSectionMapper
import com.tosin.docprocessor.data.parser.docx.mapper.DocxTableMapper
import com.tosin.docprocessor.data.parser.docx.opc.OpcPackage
import com.tosin.docprocessor.data.parser.docx.resolve.DocxNumberingResolver
import com.tosin.docprocessor.data.parser.docx.resolve.DocxStyleResolver
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.NoteKind
import com.tosin.docprocessor.domain.document.model.OpaquePackagePart
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListStyle
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

        val partDiagnostics = DiagnosticsCollector(mainPart)
        val context = DocxMapContext(mainPart, opc, styleResolver, numbering, resources, partDiagnostics)
        val blockMapper = DocxBlockMapper(context).also { it.attach(DocxTableMapper(context, it)) }

        val blocks = groupLists(blockMapper.parseBlocks(body))

        val sectionMapper = DocxSectionMapper(opc, mainPart, styleResolver, numbering, resources, partDiagnostics)
        val settings = sectionMapper.parseSettings(sectionMapper.settingsDocument())
        val sections = sectionMapper.collectSections(body, settings)
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
            blocks = blocks,
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
            outline = ParsedDocument.outlineOf(blocks)
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

    private fun groupLists(blocks: List<BlockNode>): List<BlockNode> {
        val output = mutableListOf<BlockNode>()
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            val marker = (block as? BlockNode.Paragraph)?.listMarker
            if (marker == null) {
                output += block
                index++
                continue
            }
            val items = mutableListOf<BlockNode.ListItem>()
            while (index < blocks.size) {
                val current = blocks[index] as? BlockNode.Paragraph ?: break
                val currentMarker = current.listMarker ?: break
                if (currentMarker.ordered != marker.ordered) break
                items += BlockNode.ListItem(
                    blocks = listOf(current),
                    marker = currentMarker,
                    anchor = current.anchor
                )
                index++
            }
            val kind = if (marker.ordered) ListKind.ORDERED else ListKind.UNORDERED
            output += BlockNode.ListBlock(
                kind = kind,
                style = ListStyle(kind = kind),
                items = items,
                anchor = items.firstOrNull()?.anchor
            )
        }
        return output
    }

    private fun recognizedPaths(opc: OpcPackage): Set<String> = entries.keys.filter { path ->
        path == "[Content_Types].xml" || path.endsWith(".rels") || opc.contentTypes.contentTypeFor(path) != null
    }.toSet()

    private fun preservedParts(opc: OpcPackage): List<OpaquePackagePart> = entries
        .filterKeys { opc.contentTypes.contentTypeFor(it) == null && it != "[Content_Types].xml" && !it.endsWith(".rels") }
        .map { (path, bytes) ->
            OpaquePackagePart(path = path, mimeType = null, bytes = bytes, role = "unrecognised")
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
