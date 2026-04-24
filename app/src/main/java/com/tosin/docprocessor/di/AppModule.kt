package com.tosin.docprocessor.di

import android.content.Context
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.DocumentRepositoryImpl
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
    fun provideDocumentRepository(
        @ApplicationContext context: Context
    ): DocumentRepository = DocumentRepositoryImpl(context)
}
