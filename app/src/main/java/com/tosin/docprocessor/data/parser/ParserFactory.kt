package com.tosin.docprocessor.data.parser

import android.content.Context
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.docx.DocxImageParser
import com.tosin.docprocessor.data.parser.docx.DocxParser
import com.tosin.docprocessor.data.parser.odt.OdtParser
import com.tosin.docprocessor.data.parser.text.TextParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject

class ParserFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Create a parser for the given MIME type.
     * Returns null for unsupported MIME types.
     */
    fun createParser(mimeType: String): DocumentParser? = when (mimeType) {
        MimeTypes.DOCX -> DocxParser(imageParser = DocxImageParser(context.cacheDir))
        MimeTypes.ODT -> OdtParser(context)
        MimeTypes.TXT -> TextParser()
        else -> null
    }

    /**
     * Create a parser for the given file name, resolved by extension.
     * Uses [MimeTypes.fromFileName] to determine the MIME type.
     * Returns null if the extension is not recognized.
     *
     * Use this when the MIME type from ContentResolver may be blank
     * (common with some file providers).
     */
    fun createParserByName(fileName: String): DocumentParser? {
        val mimeType = MimeTypes.fromFileName(fileName) ?: return null
        return createParser(mimeType)
    }

    /**
     * Create a blank document of the appropriate format for [fileName].
     * Picks the parser by extension, then calls [DocumentParser.createBlankDocument].
     *
     * Returns [Result.failure] with [IllegalArgumentException] if the file type
     * is not supported.
     */
    suspend fun createBlankDocument(fileName: String, outputStream: OutputStream): Result<Unit> {
        val parser = createParserByName(fileName)
            ?: return Result.failure(
                IllegalArgumentException(
                    "Unsupported file type: $fileName. Supported types: .docx, .odt, .txt"
                )
            )
        val mimeType = MimeTypes.fromFileName(fileName) ?: return Result.failure(
            IllegalArgumentException("Unsupported file type: $fileName")
        )
        return parser.createBlankDocument(outputStream, mimeType)
    }
}