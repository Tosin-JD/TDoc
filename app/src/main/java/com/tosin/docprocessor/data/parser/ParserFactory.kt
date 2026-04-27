package com.tosin.docprocessor.data.parser

import android.content.Context
import com.tosin.docprocessor.data.parser.docx.DocxParser
import com.tosin.docprocessor.data.parser.docx.DocxImageParser
import com.tosin.docprocessor.data.parser.odt.OdtParser
import com.tosin.docprocessor.data.parser.odt.OdtXmlParser
import com.tosin.docprocessor.data.common.model.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ParserFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun createParser(mimeType: String): DocumentParser? {
        return when (mimeType) {
            MimeTypes.DOCX -> DocxParser(imageParser = DocxImageParser(context.cacheDir))
            MimeTypes.ODT -> OdtParser(context)
            else -> null
        }
    }
}
