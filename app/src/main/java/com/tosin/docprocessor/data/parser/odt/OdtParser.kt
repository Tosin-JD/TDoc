package com.tosin.docprocessor.data.parser.odt

import android.content.Context
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
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

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entries = zipExtractor.extractAllEntries(inputStream)
                val contentXml = entries["content.xml"] 
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")
                
                val elements = mutableListOf<DocumentElement>()
                
                // 1. Metadata
                val metaXml = entries["meta.xml"]
                metadataParser.parse(metaXml)?.let { elements += it }
                
                // 2. Content
                val xmlParser = OdtXmlParser(context.cacheDir, entries)
                elements += xmlParser.parse(contentXml)
                
                elements
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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
            }
        }
}
