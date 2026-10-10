package com.tosin.docprocessor.data.local.dao

import com.tosin.docprocessor.data.local.entities.RecentFile
import kotlinx.coroutines.flow.Flow

interface RecentFileDao {
    fun getRecentFiles(): Flow<List<RecentFile>>

    suspend fun insertFile(file: RecentFile)

    suspend fun deleteFile(uri: String)

    suspend fun pruneOldFiles(): Int
}

