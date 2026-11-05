package com.tosin.docprocessor.data.repository

import com.tosin.docprocessor.data.local.ThemePreferences
import com.tosin.docprocessor.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRepositoryImplTest {

    private class FakeThemePreferences(initial: ThemeMode) : ThemePreferences {
        private val state = MutableStateFlow(initial)

        override val themeMode: Flow<ThemeMode> = state

        override suspend fun setThemeMode(mode: ThemeMode) {
            state.value = mode
        }
    }

    @Test
    fun `defaults to system when nothing was stored`() = runBlocking {
        val stored = ThemeMode.fromString(null)
        val repository = ThemeRepositoryImpl(FakeThemePreferences(stored))

        assertEquals(ThemeMode.SYSTEM, repository.observeThemeMode().first())
    }

    @Test
    fun `writes round-trip through the repository`() = runBlocking {
        val repository = ThemeRepositoryImpl(FakeThemePreferences(ThemeMode.SYSTEM))

        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.observeThemeMode().first())
    }

    @Test
    fun `explicit mode survives a fresh read`() = runBlocking {
        val preferences = FakeThemePreferences(ThemeMode.SYSTEM)
        ThemeRepositoryImpl(preferences).setThemeMode(ThemeMode.LIGHT)

        // A new repository over the same store must see the persisted value.
        val reopened = ThemeRepositoryImpl(preferences)
        assertEquals(ThemeMode.LIGHT, reopened.observeThemeMode().first())
    }

    @Test
    fun `invalid stored value falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString(""))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString(" dark "))
    }
}
