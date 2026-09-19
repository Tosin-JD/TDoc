package com.tosin.docprocessor.data.repository.recent

import com.tosin.docprocessor.model.DocumentMeta
import kotlinx.coroutines.flow.Flow

interface RecentFilesRepository {
    fun observeRecentFiles(): Flow<List<DocumentMeta>>
    suspend fun record(meta: DocumentMeta)
    suspend fun remove(uri: String)
    suspend fun prune()
}