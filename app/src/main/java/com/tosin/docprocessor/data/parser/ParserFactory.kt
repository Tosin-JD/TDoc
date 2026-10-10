package com.tosin.docprocessor.data.parser

import android.content.Context
import com.tosin.docprocessor.data.parser.docx.DocxDocumentParser
import com.tosin.docprocessor.data.parser.docx.DocxParser
import com.tosin.docprocessor.data.parser.odt.OdtDocumentParser
import com.tosin.docprocessor.data.parser.odt.OdtParser
import com.tosin.docprocessor.domain.model.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ParserFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Legacy parser used for writing new/blank documents. */
    fun createParser(mimeType: String): DocumentParser? {
        return when (mimeType) {
            MimeTypes.DOCX -> DocxParser(context.cacheDir)
            MimeTypes.ODT -> OdtParser(context.cacheDir)
            else -> null
        }
    }

    /**
     * Rich, read-oriented parser producing a [com.tosin.docprocessor.domain.document.ParseResult].
     * Returns null for unsupported types so callers can fall back to plain text.
     */
    fun createRichParser(bytes: ByteArray, mimeType: String): DocumentParser? {
        return when (mimeType) {
            MimeTypes.DOCX -> DocxDocumentParser.fromBytes(bytes, context.cacheDir)
            MimeTypes.ODT -> OdtDocumentParser.fromBytes(bytes, context.cacheDir)
            else -> null
        }
    }
}
