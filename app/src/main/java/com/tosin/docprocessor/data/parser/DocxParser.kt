package com.tosin.docprocessor.data.parser

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import java.io.OutputStream

class DocxParser : DocumentParser {
    override fun parse(inputStream: InputStream): String {
        return try {
            val doc = XWPFDocument(inputStream)
            val paragraphs = doc.paragraphs
            paragraphs.joinToString("\n") { it.text }
        } catch (e: Exception) {
            "Error parsing Word document: ${e.message}"
        }
    }

    override fun save(outputStream: OutputStream, content: String) {
        val doc = XWPFDocument()
        val paragraphs = content.split("\n")
        paragraphs.forEach { text ->
            val p = doc.createParagraph()
            val r = p.createRun()
            r.setText(text)
        }
        doc.write(outputStream)
    }
}
