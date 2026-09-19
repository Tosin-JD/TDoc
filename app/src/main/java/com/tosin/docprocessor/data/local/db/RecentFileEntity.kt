package com.tosin.docprocessor.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val lastOpened: Long = System.currentTimeMillis()
)