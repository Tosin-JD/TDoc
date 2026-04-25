package com.tosin.docprocessor.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.entities.RecentFile

@Database(entities = [RecentFile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
}