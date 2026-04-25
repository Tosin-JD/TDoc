package com.tosin.docprocessor.di

import android.content.Context
import androidx.room.Room
import com.tosin.docprocessor.data.local.AppDatabase
import com.tosin.docprocessor.data.local.RecentFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.DocumentRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDocumentRepository(
        @ApplicationContext context: Context
    ): DocumentRepository = DocumentRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tdoc_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRecentFileDao(database: AppDatabase): RecentFileDao {
        return database.recentFileDao()
    }
}
