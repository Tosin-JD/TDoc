package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import com.tosin.docprocessor.data.parser.util.DefaultZipExtractor
import com.tosin.docprocessor.data.parser.util.toArchiveDiagnostics
import com.tosin.docprocessor.data.parser.util.TDocLogger
import com.tosin.docprocessor.data.parser.util.ZipExtractor
import com.tosin.docprocessor.data.parser.validation.DocumentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OdtParser(
    private val cacheDir: File,
    private val zipExtractor: ZipExtractor = DefaultZipExtractor()
) : DocumentParser {

    private val metadataParser = OdtMetadataParser()
    private val validator = DocumentValidator()
    private val recoveryStrategy: RecoveryStrategy = GracefulDegradationStrategy()

    override suspend fun parse(inputStream: java.io.InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            var currentElement: String? = null
            var currentStack: List<String> = emptyList()
            var bytesProcessed = 0L
            var documentHash: String? = null
            var entryCount = 0
            var elementCount = 0
            val startTime = System.nanoTime()
            try {
                TDocLogger.info("Starting ODT parsing")
                currentElement = "ZipExtraction"
                val entries = zipExtractor.extract(inputStream)
                val diagnostics = entries.toArchiveDiagnostics("content.xml")
                bytesProcessed = diagnostics.bytesProcessed
                documentHash = diagnostics.documentHash
                entryCount = diagnostics.entryCount
                val contentXml = entries["content.xml"]
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")

                val elements = mutableListOf<DocumentElement>()
                currentElement = "Metadata"
                currentStack = listOf("meta.xml")
                metadataParser.parse(entries["meta.xml"])?.let { elements += it }
                elementCount = elements.size

                currentElement = "ContentXml"
                currentStack = listOf("content.xml")
                val xmlParser = OdtXmlParser(cacheDir, entries, recoveryStrategy)
                elements += xmlParser.parse(contentXml)
                elementCount = elements.size

                val validationResult = validator.validate(elements)
                if (!validationResult.isValid) {
                    TDocLogger.warn("Document validation failed: ${validationResult.errors}")
                }

                val durationMs = (System.nanoTime() - startTime) / 1_000_000
                TDocLogger.info(
                    "Successfully parsed ODT with ${elements.size} elements in ${durationMs}ms " +
                        "(entries=$entryCount, bytes=$bytesProcessed)"
                )
                Result.success(elements)
            } catch (e: Exception) {
                val context = ParseErrorContext(
                    currentElement = currentElement,
                    bytesProcessed = bytesProcessed,
                    elementCount = elementCount,
                    documentHash = documentHash,
                    stack = currentStack,
                    extra = mapOf("entryCount" to entryCount.toString())
                )
                TDocLogger.error(
                    "Failed to parse ODT",
                    e,
                    mapOf(
                        "lastElement" to currentElement,
                        "bytesProcessed" to bytesProcessed,
                        "elementCount" to elementCount,
                        "documentHash" to documentHash,
                        "stack" to currentStack,
                        "entryCount" to entryCount
                    )
                )
                Result.failure(ParseException("Failed to parse ODT: ${e.message}", context, e))
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ZipOutputStream(outputStream).use { zip ->
                    writeStoredEntry(zip, "mimetype", MimeTypes.ODT)
                    writeUtf8Entry(zip, "content.xml", buildContentXml(content))
                    writeUtf8Entry(zip, "styles.xml", buildStylesXml())
                    writeUtf8Entry(zip, "meta.xml", buildMetaXml())
                    writeUtf8Entry(zip, "META-INF/manifest.xml", buildManifestXml())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                TDocLogger.error("Failed to save ODT", e)
                Result.failure(e)
            }
        }

    private fun writeUtf8Entry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeStoredEntry(zip: ZipOutputStream, path: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun buildContentXml(content: List<DocumentElement>): String {
        val body = buildString {
            content.forEach { append(renderElement(it)) }
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="${OdtNamespaces.OFFICE}"
                xmlns:text="${OdtNamespaces.TEXT}"
                xmlns:table="${OdtNamespaces.TABLE}"
                xmlns:draw="${OdtNamespaces.DRAW}"
                xmlns:xlink="${OdtNamespaces.XLINK}"
                xmlns:fo="${OdtNamespaces.FO}"
                office:version="1.2">
              <office:body>
                <office:text>$body</office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()
    }

    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-styles
            xmlns:office="${OdtNamespaces.OFFICE}"
            xmlns:style="${OdtNamespaces.STYLE}"
            xmlns:text="${OdtNamespaces.TEXT}"
            xmlns:fo="${OdtNamespaces.FO}"
            office:version="1.2">
          <office:styles>
            <style:style style:name="Bold" style:family="text">
              <style:text-properties fo:font-weight="bold"/>
            </style:style>
            <style:style style:name="Italic" style:family="text">
              <style:text-properties fo:font-style="italic"/>
            </style:style>
            <style:style style:name="Underline" style:family="text">
              <style:text-properties style:text-underline-style="solid"/>
            </style:style>
            <style:style style:name="Bold_Italic" style:family="text">
              <style:text-properties fo:font-weight="bold" fo:font-style="italic"/>
            </style:style>
            <style:style style:name="Bold_Underline" style:family="text">
              <style:text-properties fo:font-weight="bold" style:text-underline-style="solid"/>
            </style:style>
            <style:style style:name="Italic_Underline" style:family="text">
              <style:text-properties fo:font-style="italic" style:text-underline-style="solid"/>
            </style:style>
            <style:style style:name="Bold_Italic_Underline" style:family="text">
              <style:text-properties fo:font-weight="bold" fo:font-style="italic" style:text-underline-style="solid"/>
            </style:style>
          </office:styles>
        </office:document-styles>
    """.trimIndent()

    private fun buildMetaXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-meta
            xmlns:office="${OdtNamespaces.OFFICE}"
            xmlns:meta="${OdtNamespaces.META}"
            xmlns:dc="${OdtNamespaces.DC}"
            office:version="1.2">
          <office:meta>
            <meta:generator>TDoc</meta:generator>
          </office:meta>
        </office:document-meta>
    """.trimIndent()

    private fun buildManifestXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <manifest:manifest
            xmlns:manifest="${OdtNamespaces.MANIFEST}"
            manifest:version="1.2">
          <manifest:file-entry manifest:media-type="application/vnd.oasis.opendocument.text" manifest:full-path="/"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="styles.xml"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="meta.xml"/>
        </manifest:manifest>
    """.trimIndent()

    private fun renderElement(element: DocumentElement): String =
        when (element) {
            is DocumentElement.Paragraph -> {
                val spans = element.spans.joinToString("") { span ->
                    val styles = mutableListOf<String>()
                    if (span.isBold) styles += "Bold"
                    if (span.isItalic) styles += "Italic"
                    if (span.isUnderline) styles += "Underline"
                    
                    if (styles.isEmpty()) {
                        escapeXml(span.text)
                    } else {
                        "<text:span text:style-name=\"${styles.joinToString("_")}\">${escapeXml(span.text)}</text:span>"
                    }
                }
                val p = "<text:p>${spans}</text:p>"
                if (element.listInfo != null) {
                    "<text:list><text:list-item>$p</text:list-item></text:list>"
                } else {
                    p
                }
            }
            is DocumentElement.SectionHeader -> "<text:h text:outline-level=\"${element.level.coerceAtLeast(1)}\">${escapeXml(element.text)}</text:h>"
            is DocumentElement.Table -> {
                val rows = element.rows.joinToString("") { row ->
                    "<table:table-row>${row.joinToString("") { cell ->
                        "<table:table-cell office:value-type=\"string\"><text:p>${escapeXml(cell)}</text:p></table:table-cell>"
                    }}</table:table-row>"
                }
                "<table:table>$rows</table:table>"
            }
            is DocumentElement.Image -> "<text:p>${escapeXml(element.caption ?: element.altText ?: "[Image]")}</text:p>"
            is DocumentElement.PageBreak -> "<text:p/>"
            is DocumentElement.Section -> "<text:p>${escapeXml(element.properties.toString())}</text:p>"
            is DocumentElement.HeaderFooter -> "<text:p>${escapeXml(element.content.text)}</text:p>"
            is DocumentElement.Note -> "<text:p>${escapeXml(element.info.text)}</text:p>"
            is DocumentElement.Comment -> "<text:p>${escapeXml(element.info.text)}</text:p>"
            is DocumentElement.Bookmark -> "<text:p>${escapeXml(element.info.name)}</text:p>"
            is DocumentElement.Field -> "<text:p>${escapeXml(element.info.instruction)}</text:p>"
            is DocumentElement.Metadata -> "<text:p>${escapeXml("${element.info.title ?: element.info.kind}: ${element.info.summary}")}</text:p>"
            is DocumentElement.Drawing -> "<text:p>${escapeXml(element.info.kind)}</text:p>"
            is DocumentElement.EmbeddedObject -> "<text:p>${escapeXml(element.info.description ?: element.info.kind)}</text:p>"
        }

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
}
