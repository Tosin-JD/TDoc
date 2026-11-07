package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.MimeTypes
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.common.PackageInventoryBuilder
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import com.tosin.docprocessor.data.parser.util.DefaultZipExtractor
import com.tosin.docprocessor.data.parser.util.TDocLogger
import com.tosin.docprocessor.data.parser.util.ZipExtractor
import com.tosin.docprocessor.data.parser.validation.DocumentValidator
import com.tosin.docprocessor.domain.document.CompatibilityLevel
import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.domain.document.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OdtParser(
    private val cacheDir: File,
    private val zipExtractor: ZipExtractor = DefaultZipExtractor()
) : DocumentParser {

    private val metadataParser = OdtMetadataParser()
    private val validator = DocumentValidator()
    private val recoveryStrategy: RecoveryStrategy = GracefulDegradationStrategy()

    override suspend fun parse(inputStream: java.io.InputStream): Result<List<DocumentElement>> =
        parseWithReport(inputStream).map { it.content }

    override suspend fun parseWithReport(inputStream: java.io.InputStream): Result<ParseResult> =
        withContext(Dispatchers.IO) {
            var currentElement: String? = null
            try {
                TDocLogger.info("Starting ODT parsing")
                currentElement = "ZipExtraction"
                val entries = zipExtractor.extract(inputStream)
                val diagnostics = mutableListOf<ParseDiagnostic>()
                validatePackage(entries, diagnostics)
                val contentXml = entries["content.xml"]
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")

                val elements = mutableListOf<DocumentElement>()
                currentElement = "Metadata"
                metadataParser.parse(entries["meta.xml"])?.let { elements += it }

                currentElement = "ContentXml"
                val xmlParser = OdtXmlParser(cacheDir, entries, entries["styles.xml"], recoveryStrategy)
                elements += xmlParser.parse(contentXml)

                val validationResult = validator.validate(elements)
                if (!validationResult.isValid) {
                    TDocLogger.warn("Document validation failed: ${validationResult.errors}")
                    diagnostics += validationResult.errors.map {
                        ParseDiagnostic(ParseDiagnostic.Severity.WARNING, it.message, construct = "document-validation")
                    }
                }

                TDocLogger.info("Successfully parsed ODT with ${elements.size} elements")
                Result.success(
                    ParseResult(
                        content = elements,
                        diagnostics = diagnostics,
                        compatibility = if (diagnostics.isEmpty()) {
                            CompatibilityLevel.RENDERED_SUPPORTED
                        } else {
                            CompatibilityLevel.UNSUPPORTED_WITH_WARNING
                        },
                        packageInventory = PackageInventoryBuilder.fromEntries(
                            entries = entries,
                            recognizedPaths = setOf("mimetype", "content.xml", "styles.xml", "meta.xml", "settings.xml", "META-INF/manifest.xml")
                        )
                    )
                )
            } catch (e: Exception) {
                val context = ParseErrorContext(currentElement = currentElement)
                TDocLogger.error("Failed to parse ODT", e, mapOf("lastElement" to currentElement))
                Result.failure(ParseException("Failed to parse ODT: ${e.message}", context, e))
            }
        }

    private fun validatePackage(
        entries: Map<String, ByteArray>,
        diagnostics: MutableList<ParseDiagnostic>
    ) {
        val mimetype = entries["mimetype"]?.toString(Charsets.UTF_8)?.trim()
        if (mimetype != null && mimetype != MimeTypes.ODT) {
            throw IllegalArgumentException("Not a valid ODT file: unexpected mimetype $mimetype")
        }
        if (mimetype == null) {
            diagnostics += ParseDiagnostic(
                severity = ParseDiagnostic.Severity.WARNING,
                message = "ODT package has no mimetype entry; continuing with guarded parsing.",
                packagePart = "mimetype",
                construct = "package"
            )
        }
        if (entries["META-INF/manifest.xml"] == null) {
            diagnostics += ParseDiagnostic(
                severity = ParseDiagnostic.Severity.WARNING,
                message = "ODT package has no manifest; resource media types cannot be fully validated.",
                packagePart = "META-INF/manifest.xml",
                construct = "package"
            )
        }
    }

}
