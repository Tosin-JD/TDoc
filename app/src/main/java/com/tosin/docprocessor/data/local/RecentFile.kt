package com.tosin.docprocessor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFile(
    @PrimaryKey val uri: String,
    val fileName: String,
    val lastAccessed: Long = System.currentTimeMillis()
)
