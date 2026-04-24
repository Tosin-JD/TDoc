package com.tosin.docprocessor.data.repository

import com.tosin.docprocessor.data.model.DocumentData
import com.tosin.docprocessor.data.parser.DocxParser
import com.tosin.docprocessor.data.parser.DocumentParser
import com.tosin.docprocessor.data.parser.OdtParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor() : DocumentRepository {
    private val parsers: List<DocumentParser> = listOf(
        DocxParser(),
        OdtParser()
    )

    override fun loadDocument(filePath: String): Flow<DocumentData> = flow {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File not found: $filePath")
            }
            val document = parseDocument(filePath)
            emit(document)
        } catch (e: Exception) {
            throw e
        }
    }

    override fun saveDocument(document: DocumentData): Flow<Unit> = flow {
        try {
            // TODO: Implement document saving logic
            emit(Unit)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun parseDocument(filePath: String): DocumentData {
        val file = File(filePath)
        val parser = parsers.find { it.canParse(file) }
            ?: throw UnsupportedOperationException("No parser found for file: ${file.name}")
        return parser.parse(file)
    }
}
