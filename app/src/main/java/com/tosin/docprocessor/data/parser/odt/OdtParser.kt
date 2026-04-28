package com.tosin.docprocessor.data.parser.odt

import android.content.Context
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.exception.ParseErrorContext
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import com.tosin.docprocessor.data.parser.util.TDocLogger
import com.tosin.docprocessor.data.parser.validation.DocumentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.odftoolkit.odfdom.doc.OdfTextDocument
import java.io.InputStream
import java.io.OutputStream

class OdtParser(
    private val context: Context
) : DocumentParser {

    private val zipExtractor = OdtZipExtractor()
    private val metadataParser = OdtMetadataParser()
    private val validator = DocumentValidator()
    private val recoveryStrategy: RecoveryStrategy = GracefulDegradationStrategy()

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            var currentElement: String? = null
            try {
                TDocLogger.info("Starting ODT parsing")
                
                currentElement = "ZipExtraction"
                val entries = zipExtractor.extractAllEntries(inputStream)
                val contentXml = entries["content.xml"] 
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")
                
                val elements = mutableListOf<DocumentElement>()
                
                // 1. Metadata
                currentElement = "Metadata"
                val metaXml = entries["meta.xml"]
                metadataParser.parse(metaXml)?.let { elements += it }
                
                // 2. Content
                currentElement = "ContentXml"
                val xmlParser = OdtXmlParser(context.cacheDir, entries, recoveryStrategy)
                elements += xmlParser.parse(contentXml)
                
                val validationResult = validator.validate(elements)
                if (!validationResult.isValid) {
                    TDocLogger.warn("Document validation failed: ${validationResult.errors}")
                }

                TDocLogger.info("Successfully parsed ODT with ${elements.size} elements")
                Result.success(elements)
            } catch (e: Exception) {
                val context = ParseErrorContext(
                    currentElement = currentElement
                )
                TDocLogger.error("Failed to parse ODT", e, mapOf("lastElement" to currentElement))
                Result.failure(ParseException("Failed to parse ODT: ${e.message}", context, e))
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                TDocLogger.info("Starting ODT save")
                // Keep the current ODFDOM implementation for saving
                val odtDoc = OdfTextDocument.newTextDocument()
                content.forEach { element ->
                    when (element) {
                        is DocumentElement.Paragraph -> odtDoc.addText(element.spans.joinToString("") { it.text } + "\n")
                        is DocumentElement.SectionHeader -> odtDoc.addText(element.text + "\n")
                        is DocumentElement.Section -> odtDoc.addText(element.properties.toString() + "\n")
                        is DocumentElement.HeaderFooter -> odtDoc.addText(element.content.text + "\n")
                        is DocumentElement.Note -> odtDoc.addText(element.info.text + "\n")
                        is DocumentElement.Comment -> odtDoc.addText(element.info.text + "\n")
                        is DocumentElement.Bookmark -> odtDoc.addText(element.info.name + "\n")
                        is DocumentElement.Field -> odtDoc.addText(element.info.instruction + "\n")
                        is DocumentElement.Metadata -> odtDoc.addText(
                            "${element.info.title ?: element.info.kind}: ${element.info.summary}\n"
                        )
                        is DocumentElement.Drawing -> odtDoc.addText(element.info.kind + "\n")
                        is DocumentElement.EmbeddedObject -> odtDoc.addText(
                            (element.info.description ?: element.info.kind) + "\n"
                        )
                        is DocumentElement.Table -> {
                            val tableText = element.rows.joinToString("\n") { row -> row.joinToString("\t") }
                            odtDoc.addText(tableText + "\n")
                        }
                        is DocumentElement.Image -> {
                            element.caption?.takeIf { it.isNotBlank() }?.let { odtDoc.addText(it + "\n") }
                        }
                        DocumentElement.PageBreak -> odtDoc.addText("\n")
                    }
                }
                odtDoc.save(outputStream)
                TDocLogger.info("Successfully saved ODT")
                Result.success(Unit)
            } catch (e: Exception) {
                TDocLogger.error("Failed to save ODT", e)
                Result.failure(e)
            }
        }
}
