package com.tosin.docprocessor.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {

    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT 20")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): RecentFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query(
        "DELETE FROM recent_files WHERE uri NOT IN " +
            "(SELECT uri FROM recent_files ORDER BY lastOpened DESC LIMIT 20)"
    )
    suspend fun prune()
}