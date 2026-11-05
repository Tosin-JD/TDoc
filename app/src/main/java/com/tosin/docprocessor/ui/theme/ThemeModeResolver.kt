package com.tosin.docprocessor.ui.theme

import com.tosin.docprocessor.domain.model.ThemeMode

/**
 * Resolves the app's light/dark state from the user preference and the device
 * setting. Pure and Compose-free so it is unit-testable on the JVM.
 */
fun resolveThemeMode(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
