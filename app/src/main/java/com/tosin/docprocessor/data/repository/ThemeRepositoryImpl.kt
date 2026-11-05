package com.tosin.docprocessor.data.repository

import com.tosin.docprocessor.data.local.ThemePreferences
import com.tosin.docprocessor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Default [ThemeRepository]: delegates persistence to [ThemePreferences]. */
class ThemeRepositoryImpl @Inject constructor(
    private val preferences: ThemePreferences
) : ThemeRepository {

    override fun observeThemeMode(): Flow<ThemeMode> = preferences.themeMode

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
}
