package com.tosin.docprocessor.data.parser

import androidx.compose.ui.text.AnnotatedString
import com.tosin.docprocessor.data.model.DocumentElement
import org.odftoolkit.odfdom.doc.OdfTextDocument
import java.io.InputStream
import java.io.OutputStream

class OdtParser : DocumentParser {
    override fun parse(inputStream: InputStream): List<DocumentElement> {
        return try {
            val odtDoc = OdfTextDocument.loadDocument(inputStream)
            val contentRoot = odtDoc.contentRoot
            val sb = StringBuilder()
            extractText(contentRoot, sb)
            listOf(DocumentElement.Paragraph(AnnotatedString(sb.toString().trim())))
        } catch (e: Exception) {
            listOf(DocumentElement.Paragraph(AnnotatedString("Error parsing ODT document: ${e.message}")))
        }
    }

    private fun extractText(node: org.w3c.dom.Node, sb: StringBuilder) {
        if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) {
            sb.append(node.textContent)
        }
        val childNodes = node.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            val localName = child.localName
            // Add newline after paragraph elements
            if (localName == "p" || localName == "h") {
                extractText(child, sb)
                sb.append("\n")
            } else {
                extractText(child, sb)
            }
        }
    }

    override fun save(outputStream: OutputStream, content: List<DocumentElement>) {
        val odtDoc = OdfTextDocument.newTextDocument()
        // Concatenate all paragraph text for simple ODT saving
        val fullText = content.filterIsInstance<DocumentElement.Paragraph>()
            .joinToString("\n") { it.content.text }
        odtDoc.addText(fullText)
        odtDoc.save(outputStream)
    }
}
