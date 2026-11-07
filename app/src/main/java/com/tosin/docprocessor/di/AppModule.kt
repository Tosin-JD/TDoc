package com.tosin.docprocessor.di

import android.content.Context
import com.tosin.docprocessor.data.local.SharedPrefsThemePreferences
import com.tosin.docprocessor.data.local.ThemePreferences
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
import com.tosin.docprocessor.data.repository.ThemeRepository
import com.tosin.docprocessor.data.repository.ThemeRepositoryImpl
import com.tosin.docprocessor.domain.typography.FontFamilyResolver
import com.tosin.docprocessor.domain.typography.TextFlowResolver
import com.tosin.docprocessor.data.writer.WriterFactory
import com.tosin.docprocessor.data.writer.common.LegacyDocumentExportAdapter

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDocumentRepository(
        @ApplicationContext context: Context,
        parserFactory: ParserFactory,
        writerFactory: WriterFactory,
        exportAdapter: LegacyDocumentExportAdapter
    ): DocumentRepository = DocumentRepositoryImpl(context, parserFactory, writerFactory, exportAdapter)

    @Provides
    @Singleton
    fun provideRecentFileDao(@ApplicationContext context: Context): RecentFileDao {
        return SharedPrefsRecentFileDao(context)
    }

    @Provides
    @Singleton
    fun provideThemePreferences(@ApplicationContext context: Context): ThemePreferences {
        return SharedPrefsThemePreferences(context)
    }

    @Provides
    @Singleton
    fun provideThemeRepository(preferences: ThemePreferences): ThemeRepository {
        return ThemeRepositoryImpl(preferences)
    }

    @Provides
    @Singleton
    fun provideFontFamilyResolver(): FontFamilyResolver = FontFamilyResolver()

    @Provides
    @Singleton
    fun provideTextFlowResolver(): TextFlowResolver = TextFlowResolver()
}
