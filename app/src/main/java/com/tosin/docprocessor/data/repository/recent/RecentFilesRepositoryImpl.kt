package com.tosin.docprocessor.data.repository.recent

import com.tosin.docprocessor.data.Mappers.toEntity
import com.tosin.docprocessor.data.Mappers.toMeta
import com.tosin.docprocessor.data.local.db.RecentFileDao
import com.tosin.docprocessor.model.DocumentMeta
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecentFilesRepositoryImpl @Inject constructor(
    private val dao: RecentFileDao
) : RecentFilesRepository {

    override fun observeRecentFiles(): Flow<List<DocumentMeta>> =
        dao.observeRecentFiles().map { entities -> entities.map { it.toMeta() } }

    override suspend fun record(meta: DocumentMeta) {
        dao.upsert(meta.toEntity())
        dao.prune()
    }

    override suspend fun remove(uri: String) {
        dao.deleteByUri(uri)
    }

    override suspend fun prune() {
        dao.prune()
    }
}