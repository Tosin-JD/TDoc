package com.tosin.docprocessor.di

import android.content.Context
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.dao.SharedPrefsRecentFileDao
import com.tosin.docprocessor.data.parser.ParserFactory
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
        @ApplicationContext context: Context,
        parserFactory: ParserFactory
    ): DocumentRepository = DocumentRepositoryImpl(context, parserFactory)

    @Provides
    @Singleton
    fun provideRecentFileDao(@ApplicationContext context: Context): RecentFileDao {
        return SharedPrefsRecentFileDao(context)
    }
}
