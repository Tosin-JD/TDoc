package com.tosin.docprocessor.data.repository

import com.tosin.docprocessor.data.model.DocumentData
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun loadDocument(filePath: String): Flow<DocumentData>
    fun saveDocument(document: DocumentData): Flow<Unit>
    suspend fun parseDocument(filePath: String): DocumentData
}
