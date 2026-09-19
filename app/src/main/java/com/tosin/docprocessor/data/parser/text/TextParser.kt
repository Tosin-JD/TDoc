package com.tosin.docprocessor.data.parser.text

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Text parser for `.txt` files so plain text round-trips symmetrically.
 */
class TextParser : DocumentParser {

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            try {
                val text = inputStream.bufferedReader(charset = Charsets.UTF_8).use { it.readText() }
                val elements = text.lines()
                    .filter { it.isNotBlank() }
                    .map { line ->
                        DocumentElement.Paragraph(
                            spans = listOf(TextSpan(text = line, color = "000000"))
                        )
                    }
                Result.success(elements)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                outputStream.bufferedWriter(charset = Charsets.UTF_8).use { writer ->
                    writer.write(PlainTextFlattener.toPlainText(content))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun createBlankDocument(
        outputStream: OutputStream,
        mimeType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            outputStream.bufferedWriter(charset = Charsets.UTF_8).use { it.write("") }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}