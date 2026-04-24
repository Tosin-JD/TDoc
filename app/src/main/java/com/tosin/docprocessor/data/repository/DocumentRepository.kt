package com.tosin.docprocessor.data.repository

import android.net.Uri
import com.tosin.docprocessor.data.model.DocumentData
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    suspend fun readTextFromUri(uri: Uri): AnnotatedString
    suspend fun saveTextToUri(uri: Uri, content: AnnotatedString)
    suspend fun getFileName(uri: Uri): String
    fun loadDocument(filePath: String): Flow<DocumentData>
    fun saveDocument(document: DocumentData): Flow<Unit>
    suspend fun parseDocument(filePath: String): DocumentData
}
