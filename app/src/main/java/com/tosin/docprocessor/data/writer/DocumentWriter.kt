package com.tosin.docprocessor.data.writer

import com.tosin.docprocessor.domain.document.model.ParsedDocument
import java.io.OutputStream

/** Writes and finalizes an archive without taking ownership of the supplied stream. */
interface DocumentWriter {
    suspend fun write(document: ParsedDocument, outputStream: OutputStream)
}
