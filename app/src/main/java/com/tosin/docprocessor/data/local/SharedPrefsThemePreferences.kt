package com.tosin.docprocessor.data.local

import android.content.Context
import com.tosin.docprocessor.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * SharedPreferences-backed [ThemePreferences], following the same
 * `MutableStateFlow` pattern as `SharedPrefsRecentFileDao`.
 */
class SharedPrefsThemePreferences @Inject constructor(
    @ApplicationContext context: Context
) : ThemePreferences {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storedMode = MutableStateFlow(
        ThemeMode.fromString(preferences.getString(KEY_THEME_MODE, null))
    )

    override val themeMode: Flow<ThemeMode> = storedMode

    override suspend fun setThemeMode(mode: ThemeMode) {
        if (storedMode.value == mode) return
        storedMode.value = mode
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "tdoc_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
