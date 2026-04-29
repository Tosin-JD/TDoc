package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import com.tosin.docprocessor.data.parser.util.DefaultZipExtractor
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
            try {
                TDocLogger.info("Starting ODT parsing")
                currentElement = "ZipExtraction"
                val entries = zipExtractor.extract(inputStream)
                val contentXml = entries["content.xml"]
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")

                val elements = mutableListOf<DocumentElement>()
                currentElement = "Metadata"
                metadataParser.parse(entries["meta.xml"])?.let { elements += it }

                currentElement = "ContentXml"
                val xmlParser = OdtXmlParser(cacheDir, entries, recoveryStrategy)
                elements += xmlParser.parse(contentXml)

                val validationResult = validator.validate(elements)
                if (!validationResult.isValid) {
                    TDocLogger.warn("Document validation failed: ${validationResult.errors}")
                }

                TDocLogger.info("Successfully parsed ODT with ${elements.size} elements")
                Result.success(elements)
            } catch (e: Exception) {
                val context = ParseErrorContext(currentElement = currentElement)
                TDocLogger.error("Failed to parse ODT", e, mapOf("lastElement" to currentElement))
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
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
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
            xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
            xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
            office:version="1.2">
          <office:styles>
            <style:style style:name="Heading_20_1" style:family="paragraph">
              <style:paragraph-properties/>
            </style:style>
          </office:styles>
        </office:document-styles>
    """.trimIndent()

    private fun buildMetaXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-meta
            xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
            xmlns:dc="http://purl.org/dc/elements/1.1/"
            office:version="1.2">
          <office:meta>
            <meta:generator>TDoc</meta:generator>
          </office:meta>
        </office:document-meta>
    """.trimIndent()

    private fun buildManifestXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <manifest:manifest
            xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
            manifest:version="1.2">
          <manifest:file-entry manifest:media-type="application/vnd.oasis.opendocument.text" manifest:full-path="/"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="styles.xml"/>
          <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="meta.xml"/>
        </manifest:manifest>
    """.trimIndent()

    private fun renderElement(element: DocumentElement): String =
        when (element) {
            is DocumentElement.Paragraph -> "<text:p>${escapeXml(element.spans.joinToString("") { it.text })}</text:p>"
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
