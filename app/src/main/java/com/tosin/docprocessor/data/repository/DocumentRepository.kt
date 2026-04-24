package com.tosin.docprocessor.data.repository

import android.net.Uri
import com.tosin.docprocessor.data.model.DocumentData
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun readTextFromUri(uri: Uri): String
    suspend fun saveTextToUri(uri: Uri, content: String)
    suspend fun getFileName(uri: Uri): String
    fun loadDocument(filePath: String): Flow<DocumentData>
    fun saveDocument(document: DocumentData): Flow<Unit>
    suspend fun parseDocument(filePath: String): DocumentData
}
