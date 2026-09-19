package com.tosin.docprocessor

import android.net.Uri
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.recent.RecentFilesRepository
import com.tosin.docprocessor.model.DocumentMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeDocumentRepository(
    var meta: DocumentMeta = DocumentMeta(uri = "content://authority/doc/doc.txt", fileName = "doc.txt", mimeType = MimeTypes.TXT)
) : DocumentRepository {

var content: List<DocumentElement> = listOf(
        DocumentElement.Paragraph(spans = listOf(com.tosin.docprocessor.data.parser.internal.models.TextSpan(text = "Hello document")))
    )
    var readError: Throwable? = null
    var metaError: Throwable? = null
    var saveError: Throwable? = null
    var createError: Throwable? = null

    val savedUris = mutableListOf<String>()
    val createdUris = mutableListOf<String>()

    override suspend fun readDocumentFromUri(uri: Uri): DocumentData {
        readError?.let { throw it }
        return DocumentData(
            id = uri.toString(),
            filename = meta.fileName,
            content = content,
            format = "txt"
        )
    }

    override suspend fun getDocumentMeta(uri: Uri): DocumentMeta {
        metaError?.let { throw it }
        return meta.copy(uri = uri.toString())
    }

    override suspend fun getFileName(uri: Uri): String {
        metaError?.let { throw it }
        return meta.fileName
    }

    override suspend fun saveDocumentToUri(uri: Uri, content: List<DocumentElement>) {
        saveError?.let { throw it }
        savedUris += uri.toString()
    }

    override suspend fun createNewDocument(uri: Uri, fileName: String) {
        createError?.let { throw it }
        createdUris += uri.toString()
    }
}

class FakeRecentFilesRepository : RecentFilesRepository {

    val recents = MutableStateFlow<List<DocumentMeta>>(emptyList())
    var observeError: Throwable? = null

    val recorded = mutableListOf<DocumentMeta>()
    val removed = mutableListOf<String>()
    var pruneCount = 0

    override fun observeRecentFiles(): Flow<List<DocumentMeta>> {
        observeError?.let { return flow { throw it } }
        return recents
    }

    override suspend fun record(meta: DocumentMeta) {
        recorded += meta
    }

    override suspend fun remove(uri: String) {
        removed += uri
    }

    override suspend fun prune() {
        pruneCount++
    }
}