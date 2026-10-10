package com.tosin.docprocessor.data.repository

import android.net.Uri
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun readDocumentFromUri(uri: Uri): ParseResult
    suspend fun saveDocumentToUri(uri: Uri, content: List<DocumentElement>)
    suspend fun getFileName(uri: Uri): String
    fun loadDocument(filePath: String): Flow<DocumentData>
    fun saveDocument(document: DocumentData): Flow<Unit>
    suspend fun parseDocument(filePath: String): DocumentData
    suspend fun createNewDocument(uri: Uri)
}
