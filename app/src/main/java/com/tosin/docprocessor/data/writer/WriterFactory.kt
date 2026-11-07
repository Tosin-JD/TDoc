package com.tosin.docprocessor.data.writer

import com.tosin.docprocessor.data.writer.docx.DocxDocumentWriter
import com.tosin.docprocessor.data.writer.odt.OdtDocumentWriter
import com.tosin.docprocessor.domain.model.MimeTypes
import javax.inject.Inject

class WriterFactory @Inject constructor(
    private val docxWriter: DocxDocumentWriter,
    private val odtWriter: OdtDocumentWriter
) {
    fun createWriter(mimeType: String): DocumentWriter? = when (mimeType.lowercase()) {
        MimeTypes.DOCX -> docxWriter
        MimeTypes.ODT -> odtWriter
        else -> null
    }
}
