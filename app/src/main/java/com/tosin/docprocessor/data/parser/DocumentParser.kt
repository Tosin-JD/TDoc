package com.tosin.docprocessor.data.parser


import com.tosin.docprocessor.data.common.model.DocumentElement
import java.io.InputStream
import java.io.OutputStream

interface DocumentParser {
    /**
     * Parses the [inputStream] into a list of [DocumentElement].
     * We use 'suspend' to ensure this runs off the main thread.
     */
    suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>>

    /**
     * Encodes the [content] and writes it to the [outputStream].
     */
    suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit>

    /**
     * Creates an empty document in this format and writes it to the [outputStream].
     * Used by the "New document" flow so the repository never depends on office libraries.
     */
    suspend fun createBlankDocument(
        outputStream: OutputStream,
        mimeType: String
    ): Result<Unit> = Result.failure(UnsupportedOperationException("createBlankDocument not supported for $mimeType"))
}
