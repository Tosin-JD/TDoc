package com.tosin.docprocessor.data.parser

import com.tosin.docprocessor.data.model.DocumentData
import java.io.File

class DocxParser : DocumentParser {
    override fun parse(file: File): DocumentData {
        // TODO: Implement DOCX parsing logic
        // DOCX files are ZIP archives containing XML
        return DocumentData(
            id = file.nameWithoutExtension,
            filename = file.name,
            content = "TODO: Parse DOCX content",
            format = "docx"
        )
    }

    override fun canParse(file: File): Boolean {
        return file.extension.lowercase() == "docx"
    }
}
