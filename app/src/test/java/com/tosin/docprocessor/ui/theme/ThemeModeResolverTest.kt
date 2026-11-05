package com.tosin.docprocessor.ui.theme

import com.tosin.docprocessor.domain.model.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeResolverTest {

    @Test
    fun `system mode follows a light device`() {
        assertFalse(resolveThemeMode(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `system mode follows a dark device`() {
        assertTrue(resolveThemeMode(ThemeMode.SYSTEM, systemInDarkTheme = true))
    }

    @Test
    fun `explicit light overrides a dark device`() {
        assertFalse(resolveThemeMode(ThemeMode.LIGHT, systemInDarkTheme = true))
    }

    @Test
    fun `explicit light stays light on a light device`() {
        assertFalse(resolveThemeMode(ThemeMode.LIGHT, systemInDarkTheme = false))
    }

    @Test
    fun `explicit dark overrides a light device`() {
        assertTrue(resolveThemeMode(ThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `explicit dark stays dark on a dark device`() {
        assertTrue(resolveThemeMode(ThemeMode.DARK, systemInDarkTheme = true))
    }
}
