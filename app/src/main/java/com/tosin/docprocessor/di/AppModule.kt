package com.tosin.docprocessor.di

import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.DocumentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository
}
