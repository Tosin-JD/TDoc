package com.tosin.docprocessor.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.repository.ThemeRepository
import com.tosin.docprocessor.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the theme preference. The resolved dark/light boolean is computed at the
 * app root from this mode plus the system setting, then shared through
 * [LocalAppIsDarkTheme].
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repository: ThemeRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM
        )

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    /** Menu switch: an explicit light/dark choice overrides the system. */
    fun setDarkMode(enabled: Boolean) {
        setMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun useSystemTheme() {
        setMode(ThemeMode.SYSTEM)
    }
}
