package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.odftoolkit.odfdom.doc.OdfTextDocument
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class OdtParser(
    private val xmlParser: OdtXmlParser = OdtXmlParser()
) : DocumentParser {

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            try {
                val elements = mutableListOf<DocumentElement>()
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "content.xml") {
                            val xml = zip.readBytes().decodeToString()
                            elements += xmlParser.parse(xml)
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
                Result.success(elements)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
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
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
