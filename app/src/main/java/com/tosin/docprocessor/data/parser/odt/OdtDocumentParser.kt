package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.common.CaptionAttacher
import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.LegacyElementAdapter
import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.PackageInventoryBuilder
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.odt.mapper.OdfBlockMapper
import com.tosin.docprocessor.data.parser.odt.mapper.OdfFrameMapper
import com.tosin.docprocessor.data.parser.odt.mapper.OdfInlineMapper
import com.tosin.docprocessor.data.parser.odt.mapper.OdfMapContext
import com.tosin.docprocessor.data.parser.odt.mapper.OdfMetadataMapper
import com.tosin.docprocessor.data.parser.odt.mapper.OdfSectionMapper
import com.tosin.docprocessor.data.parser.odt.mapper.OdfTableMapper
import com.tosin.docprocessor.data.parser.odt.opc.OdfPackage
import com.tosin.docprocessor.data.parser.odt.resolve.OdfStyleResolver
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.domain.document.model.OpaquePackagePart
import com.tosin.docprocessor.domain.document.model.ParsedDocument
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
 * Rich OpenDocument Text parser. Produces a [ParsedDocument] plus the temporary
 * legacy projection. Read-only; export is delegated to the legacy parser.
 */
class OdtDocumentParser(
    private val entries: Map<String, ByteArray>,
    private val cacheDir: File? = null,
    private val diagnostics: DiagnosticsCollector = DiagnosticsCollector(),
    private val resources: ResourceCollector = ResourceCollector()
) : DocumentParser {

    private val legacySaver: OdtParser? = cacheDir?.let { OdtParser(it) }

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
        val opc = OdfPackage.parse(entries)
        if (opc.manifest.encryptedPaths.contains("content.xml")) {
            throw IllegalArgumentException("Encrypted ODT documents are not supported")
        }
        val contentRoot = opc.document("content.xml")?.documentElement
            ?: throw IllegalArgumentException("Not a valid ODT: unable to read content.xml")
        val stylesRoot = opc.document("styles.xml")?.documentElement
        val partDiagnostics = DiagnosticsCollector("content.xml")

        val styleResolver = OdfStyleResolver.parse(listOfNotNull(stylesRoot), contentRoot)
        val context = OdfMapContext("content.xml", styleResolver, resources, partDiagnostics, entries)

        val frameMapper = OdfFrameMapper(context)
        val inlineMapper = OdfInlineMapper(context, frameMapper)
        val tableMapper = OdfTableMapper(context)
        val blockMapper = OdfBlockMapper(context, inlineMapper, tableMapper, frameMapper)
        inlineMapper.attach(blockMapper)
        tableMapper.attach(blockMapper)
        frameMapper.attach(blockMapper)

        val body = contentRoot.firstNs(Ns.OFFICE, "body")
        val text = body?.firstNs(Ns.OFFICE, "text")
            ?: throw IllegalArgumentException("Not a valid ODT: missing office:text body")
        val blocks = CaptionAttacher.attach(blockMapper.parseBlocks(text))

        val sectionMapper = OdfSectionMapper(context, blockMapper)
        val sections = sectionMapper.collectSections(stylesRoot)
        val boundBlocks = sectionMapper.bindBlocks(blocks, sections)
        val metadata = OdfMetadataMapper().parse(opc.document("meta.xml")?.documentElement)
        val settings = sectionMapper.parseSettings(opc.document("settings.xml")?.documentElement)
        diagnostics.merge(partDiagnostics)

        val parsed = ParsedDocument(
            blocks = boundBlocks,
            metadata = metadata,
            styles = styleResolver.catalogue(),
            resources = resources.resources(),
            sections = sections,
            headerFooters = sectionMapper.collectHeaderFooters(stylesRoot),
            comments = inlineMapper.collectedComments(),
            notes = inlineMapper.collectedNotes(),
            settings = settings,
            sourceFormat = SourceFormat.ODT,
            diagnostics = diagnostics.diagnostics,
            compatibility = diagnostics.compatibilityLevel(),
            opaqueParts = preservedParts(opc),
            outline = ParsedDocument.outlineOf(boundBlocks)
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

    private fun recognizedPaths(opc: OdfPackage): Set<String> = entries.keys.filter { path ->
        path == "mimetype" || path == "META-INF/manifest.xml" || opc.manifest.mediaTypes.containsKey(path)
    }.toSet()

    private fun preservedParts(opc: OdfPackage): List<OpaquePackagePart> = entries
        .filterKeys { it !in recognizedPaths(opc) }
        .map { (path, bytes) ->
            OpaquePackagePart(
                path = path,
                mimeType = opc.manifest.mediaTypes[path],
                bytes = bytes,
                role = "unrecognised"
            )
        }

    companion object {
        fun fromBytes(bytes: ByteArray, cacheDir: File? = null): OdtDocumentParser {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
            return OdtDocumentParser(entries, cacheDir)
        }

        fun read(inputStream: InputStream, cacheDir: File? = null): OdtDocumentParser =
            fromBytes(inputStream.readBytes(), cacheDir)
    }
}
