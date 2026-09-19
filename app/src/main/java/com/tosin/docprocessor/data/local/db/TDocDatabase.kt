package com.tosin.docprocessor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecentFileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TDocDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
}