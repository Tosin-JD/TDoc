package com.tosin.docprocessor.data

import com.tosin.docprocessor.data.local.db.RecentFileEntity
import com.tosin.docprocessor.model.DocumentMeta

/**
 * Single place for entity <-> app-model conversions.
 */
object Mappers {

    fun RecentFileEntity.toMeta(): DocumentMeta =
        DocumentMeta(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastOpened = lastOpened
        )

    fun DocumentMeta.toEntity(): RecentFileEntity =
        RecentFileEntity(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastOpened = lastOpened
        )
}