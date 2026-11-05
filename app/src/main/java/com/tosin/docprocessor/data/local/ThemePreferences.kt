package com.tosin.docprocessor.data.local

import com.tosin.docprocessor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Persistent store for the user's theme preference. Kept as an interface so the
 * repository can be unit-tested with an in-memory implementation.
 */
interface ThemePreferences {
    /** Emits the stored mode immediately, then on every write. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
