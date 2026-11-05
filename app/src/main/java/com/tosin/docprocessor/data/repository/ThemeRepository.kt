package com.tosin.docprocessor.data.repository

import com.tosin.docprocessor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Observed and persisted theme preference. */
interface ThemeRepository {
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
