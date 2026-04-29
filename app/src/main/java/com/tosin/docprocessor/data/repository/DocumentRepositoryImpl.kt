package com.tosin.docprocessor.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.ParserFactory
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parserFactory: ParserFactory
) : DocumentRepository {

    override suspend fun readDocumentFromUri(uri: Uri): List<DocumentElement> {
        val contentResolver = context.contentResolver
        val fileName = getFileName(uri)
        val mimeType = contentResolver.getType(uri) ?: MimeTypes.fromExtension(fileName.substringAfterLast('.', "")) ?: ""

        return contentResolver.openInputStream(uri)?.use { inputStream ->
            parseStream(inputStream, mimeType)
        } ?: plainTextDocument("Failed to open file")
    }

    override suspend fun saveDocumentToUri(uri: Uri, content: List<DocumentElement>) {
        val fileName = getFileName(uri)
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypes.fromExtension(fileName.substringAfterLast('.', ""))
            ?: ""
        context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
            val parser = parserFactory.createParser(mimeType)
            if (parser != null) {
                parser.save(outputStream, content).getOrThrow()
            } else {
                outputStream.bufferedWriter().use { it.write(content.toPlainText()) }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    override suspend fun getFileName(uri: Uri): String {
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    override fun loadDocument(filePath: String): Flow<DocumentData> = flow {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        val document = parseDocument(filePath)
        emit(document)
    }

    override fun saveDocument(document: DocumentData): Flow<Unit> = flow {
        val file = File(document.id.ifEmpty { document.filename })
        file.outputStream().use { outputStream ->
            val mimeType = MimeTypes.fromExtension(document.format.lowercase()).orEmpty()
            val parser = parserFactory.createParser(mimeType)
            if (parser != null) {
                parser.save(outputStream, document.content).getOrThrow()
            } else {
                outputStream.bufferedWriter().use { it.write(document.content.toPlainText()) }
            }
        }
        emit(Unit)
    }

    override suspend fun parseDocument(filePath: String): DocumentData =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            val fileName = file.name
            val mimeType = MimeTypes.fromExtension(file.extension).orEmpty()
            val content = file.inputStream().use { inputStream -> parseStream(inputStream, mimeType) }

            DocumentData(
                id = file.absolutePath,
                filename = fileName,
                content = content,
                format = file.extension
            )
        }

    override suspend fun createNewDocument(uri: Uri) {
        val fileName = getFileName(uri)
        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypes.fromExtension(fileName.substringAfterLast('.', ""))
            ?: ""
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val parser = parserFactory.createParser(mimeType)
            if (parser != null) {
                parser.save(outputStream, emptyList()).getOrThrow()
            } else {
                outputStream.bufferedWriter().use { it.write("") }
            }
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    private suspend fun parseStream(
        inputStream: java.io.InputStream,
        mimeType: String
    ): List<DocumentElement> {
        val parser = parserFactory.createParser(mimeType)
        return parser?.parse(inputStream)?.getOrThrow()
            ?: plainTextDocument(inputStream.bufferedReader().use { it.readText() })
    }

    private fun plainTextDocument(text: String): List<DocumentElement> =
        listOf(DocumentElement.Paragraph(spans = listOf(TextSpan(text = text, color = "000000"))))

    private fun List<DocumentElement>.toPlainText(): String =
        joinToString("\n") { element ->
            when (element) {
                is DocumentElement.Paragraph -> buildString {
                    element.listLabel?.let {
                        append(it)
                        append(' ')
                    }
                    append(element.spans.joinToString("") { it.text })
                }
                is DocumentElement.SectionHeader -> element.text
                is DocumentElement.Section -> element.properties.toString()
                is DocumentElement.HeaderFooter -> element.content.text
                is DocumentElement.Note -> element.info.text
                is DocumentElement.Comment -> element.info.text
                is DocumentElement.Bookmark -> element.info.name
                is DocumentElement.Field -> element.info.instruction
                is DocumentElement.Metadata -> "${element.info.title ?: element.info.kind}: ${element.info.summary}"
                is DocumentElement.Drawing -> element.info.kind
                is DocumentElement.EmbeddedObject -> element.info.description ?: element.info.kind
                is DocumentElement.Table -> element.rows.joinToString("\n") { row -> row.joinToString("\t") }
                is DocumentElement.Image -> element.caption ?: element.altText.orEmpty()
                is DocumentElement.PageBreak -> ""
            }
        }
}
