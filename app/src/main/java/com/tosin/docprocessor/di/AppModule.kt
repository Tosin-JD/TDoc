package com.tosin.docprocessor.di

import android.content.Context
import androidx.room.Room
import com.tosin.docprocessor.data.local.db.RecentFileDao
import com.tosin.docprocessor.data.local.db.TDocDatabase
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.DocumentRepositoryImpl
import com.tosin.docprocessor.data.repository.recent.RecentFilesRepository
import com.tosin.docprocessor.data.repository.recent.RecentFilesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TDocDatabase =
        Room.databaseBuilder(context, TDocDatabase::class.java, "tdoc.db").build()

    @Provides
    fun provideRecentFileDao(database: TDocDatabase): RecentFileDao = database.recentFileDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindRecentFilesRepository(impl: RecentFilesRepositoryImpl): RecentFilesRepository
}