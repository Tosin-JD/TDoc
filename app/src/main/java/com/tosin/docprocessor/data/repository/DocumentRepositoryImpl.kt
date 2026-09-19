package com.tosin.docprocessor.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.ParserFactory
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.parser.text.PlainTextFlattener
import com.tosin.docprocessor.model.DocumentMeta
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parserFactory: ParserFactory
) : DocumentRepository {

    override suspend fun readDocumentFromUri(uri: Uri): DocumentData = withContext(Dispatchers.IO) {
        val meta = getDocumentMeta(uri)
        val contentResolver = context.contentResolver
        val content = contentResolver.openInputStream(uri)?.use { inputStream ->
            val parser = parserFactory.createParser(meta.mimeType)
                ?: parserFactory.createParserByName(meta.fileName)
            if (parser != null) {
                parser.parse(inputStream).getOrThrow()
            } else {
                val text = inputStream.readBytes().toString(Charsets.UTF_8)
                listOf(
                    DocumentElement.Paragraph(
                        spans = listOf(TextSpan(text = text, color = "000000"))
                    )
                )
            }
        } ?: throw IllegalStateException("Failed to open file: $uri")

        DocumentData(
            id = uri.toString(),
            filename = meta.fileName,
            content = content,
            format = meta.fileName.substringAfterLast('.', "").lowercase()
        )
    }

    override suspend fun getDocumentMeta(uri: Uri): DocumentMeta = withContext(Dispatchers.IO) {
        var fileName = ""
        var sizeBytes = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: ""
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypes.fromFileName(fileName)
            ?: MimeTypes.TXT

        DocumentMeta(
            uri = uri.toString(),
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastOpened = System.currentTimeMillis()
        )
    }

    override suspend fun getFileName(uri: Uri): String =
        getDocumentMeta(uri).fileName

    override suspend fun saveDocumentToUri(
        uri: Uri,
        content: List<DocumentElement>
    ): Unit = withContext(Dispatchers.IO) {
        val meta = getDocumentMeta(uri)
        val parser = parserFactory.createParser(meta.mimeType)
            ?: parserFactory.createParserByName(meta.fileName)

        // Serialize fully to memory first so a mid-write failure never corrupts the file.
        val bytes = ByteArrayOutputStream().use { buffer ->
            if (parser != null) {
                parser.save(buffer, content).getOrThrow()
            } else {
                buffer.bufferedWriter(charset = Charsets.UTF_8).use {
                    it.write(PlainTextFlattener.toPlainText(content))
                }
            }
            buffer.toByteArray()
        }

        val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Could not open output stream for URI: $uri")
        outputStream.use { it.write(bytes) }
    }

    override suspend fun createNewDocument(uri: Uri, fileName: String): Unit =
        withContext(Dispatchers.IO) {
            val parser = parserFactory.createParserByName(fileName)
                ?: throw IllegalArgumentException("Unsupported document format: $fileName")
            val mimeType = MimeTypes.fromFileName(fileName)
                ?: throw IllegalArgumentException("Unsupported document format: $fileName")

            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Could not open output stream for URI: $uri")
            outputStream.use { stream ->
                parser.createBlankDocument(stream, mimeType).getOrThrow()
            }
        }
}