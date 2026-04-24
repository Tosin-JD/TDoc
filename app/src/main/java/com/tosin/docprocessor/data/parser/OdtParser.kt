package com.tosin.docprocessor.data.parser

import org.odftoolkit.odfdom.doc.OdfTextDocument
import org.odftoolkit.odfdom.incubator.search.TextNavigation
import java.io.InputStream
import java.io.OutputStream

class OdtParser : DocumentParser {
    override fun parse(inputStream: InputStream): String {
        return try {
            val odtDoc = OdfTextDocument.loadDocument(inputStream)
            // Extract all text content from the ODT document
            val contentRoot = odtDoc.contentRoot
            val sb = StringBuilder()
            extractText(contentRoot, sb)
            sb.toString().trim()
        } catch (e: Exception) {
            "Error parsing ODT document: ${e.message}"
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

    override fun save(outputStream: OutputStream, content: String) {
        val odtDoc = OdfTextDocument.newTextDocument()
        odtDoc.addText(content)
        odtDoc.save(outputStream)
    }
}
