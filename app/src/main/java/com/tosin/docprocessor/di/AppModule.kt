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
import com.tosin.docprocessor.domain.typography.FontFamilyResolver
import com.tosin.docprocessor.domain.typography.TextFlowResolver
import com.tosin.docprocessor.domain.pagination.PaginationEngineFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePaginationEngineFactory(@ApplicationContext context: Context): PaginationEngineFactory {
        return PaginationEngineFactory(context)
    }

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

    @Provides
    @Singleton
    fun provideFontFamilyResolver(): FontFamilyResolver = FontFamilyResolver()

    @Provides
    @Singleton
    fun provideTextFlowResolver(): TextFlowResolver = TextFlowResolver()
}
