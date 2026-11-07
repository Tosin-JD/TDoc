package com.tosin.docprocessor.data.writer.docx

import com.tosin.docprocessor.data.writer.DocumentWriter
import com.tosin.docprocessor.data.writer.common.ZipPackageWriter
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

class DocxDocumentWriter @Inject constructor(
) : DocumentWriter {
    private val packageBuilder = DocxPackageBuilder()

    override suspend fun write(document: ParsedDocument, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            ZipPackageWriter.write(packageBuilder.build(document), outputStream)
        }
    }
}
