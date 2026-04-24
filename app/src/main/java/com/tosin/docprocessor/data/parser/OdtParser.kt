package com.tosin.docprocessor.data.parser

import com.tosin.docprocessor.data.model.DocumentData
import java.io.File

class OdtParser : DocumentParser {
    override fun parse(file: File): DocumentData {
        // TODO: Implement ODT parsing logic
        // ODT files are ZIP archives containing XML
        return DocumentData(
            id = file.nameWithoutExtension,
            filename = file.name,
            content = "TODO: Parse ODT content",
            format = "odt"
        )
    }

    override fun canParse(file: File): Boolean {
        return file.extension.lowercase() == "odt"
    }
}
