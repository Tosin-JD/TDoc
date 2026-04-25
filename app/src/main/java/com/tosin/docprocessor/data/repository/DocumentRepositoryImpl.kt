package com.tosin.docprocessor.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.AnnotatedString
import com.tosin.docprocessor.data.model.DocumentData
import com.tosin.docprocessor.data.parser.DocxParser
import com.tosin.docprocessor.data.parser.OdtParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRepository {

    override suspend fun readTextFromUri(uri: Uri): AnnotatedString {
        val contentResolver = context.contentResolver
        val fileName = getFileName(uri)

        return contentResolver.openInputStream(uri)?.use { inputStream ->
            when {
                fileName.endsWith(".docx", ignoreCase = true) -> DocxParser().parse(inputStream)
                fileName.endsWith(".odt", ignoreCase = true) -> OdtParser().parse(inputStream)
                else -> AnnotatedString(inputStream.bufferedReader().use { it.readText() })
            }
        } ?: AnnotatedString("Failed to open file")
    }

    override suspend fun saveTextToUri(uri: Uri, content: AnnotatedString) {
        val fileName = getFileName(uri)
        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            when {
                fileName.endsWith(".docx", ignoreCase = true) -> DocxParser().save(outputStream, content)
                fileName.endsWith(".odt", ignoreCase = true) -> OdtParser().save(outputStream, content)
                else -> outputStream.bufferedWriter().use { it.write(content.text) }
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
            when (document.format.lowercase()) {
                "docx" -> DocxParser().save(outputStream, document.content)
                "odt" -> OdtParser().save(outputStream, document.content)
                else -> outputStream.bufferedWriter().use { it.write(document.content.text) }
            }
        }
        emit(Unit)
    }

    override suspend fun parseDocument(filePath: String): DocumentData {
        val file = File(filePath)
        val content = file.inputStream().use { inputStream ->
            when {
                file.name.endsWith(".docx", ignoreCase = true) -> DocxParser().parse(inputStream)
                file.name.endsWith(".odt", ignoreCase = true) -> OdtParser().parse(inputStream)
                else -> AnnotatedString(inputStream.bufferedReader().use { it.readText() })
            }
        }
        return DocumentData(
            id = file.nameWithoutExtension,
            filename = file.name,
            content = content,
            format = file.extension
        )
    }

    override suspend fun createNewDocument(uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            // Create a blank Word document structure
            val doc = XWPFDocument()
            doc.write(outputStream)
        }
    }
}
