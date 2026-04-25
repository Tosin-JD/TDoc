package com.tosin.docprocessor.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tosin.docprocessor.data.local.entities.RecentFile
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessed DESC LIMIT 5")
    fun getRecentFiles(): Flow<List<RecentFile>>

    @Upsert
    fun insertFile(file: RecentFile)
}