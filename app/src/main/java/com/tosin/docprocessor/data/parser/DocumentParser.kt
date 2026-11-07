package com.tosin.docprocessor.data.parser


import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.document.ParseResult
import java.io.InputStream

interface DocumentParser {
    /**
     * Parses the [inputStream] into a list of [DocumentElement].
     * We use 'suspend' to ensure this runs off the main thread.
     */
    suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>>

    /**
     * New callers use this richer result. Existing parsers and renderers remain
     * source-compatible while format-specific parsers are migrated gradually.
     */
    suspend fun parseWithReport(inputStream: InputStream): Result<ParseResult> =
        parse(inputStream).map { ParseResult(content = it) }
}
