package com.tosin.docprocessor.data.repository

import android.net.Uri
import com.tosin.docprocessor.data.model.DocumentData
import com.tosin.docprocessor.data.model.DocumentElement
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun readDocumentFromUri(uri: Uri): List<DocumentElement>
    suspend fun saveDocumentToUri(uri: Uri, content: List<DocumentElement>)
    suspend fun getFileName(uri: Uri): String
    fun loadDocument(filePath: String): Flow<DocumentData>
    fun saveDocument(document: DocumentData): Flow<Unit>
    suspend fun parseDocument(filePath: String): DocumentData
    suspend fun createNewDocument(uri: Uri)
}
