package com.tosin.docprocessor.data.repository

import android.net.Uri
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.model.DocumentMeta

interface DocumentRepository {
    suspend fun readDocumentFromUri(uri: Uri): DocumentData
    suspend fun getDocumentMeta(uri: Uri): DocumentMeta
    suspend fun getFileName(uri: Uri): String
    suspend fun saveDocumentToUri(uri: Uri, content: List<DocumentElement>)
    suspend fun createNewDocument(uri: Uri, fileName: String)
}