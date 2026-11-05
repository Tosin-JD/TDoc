package com.tosin.docprocessor.ui.theme

import com.tosin.docprocessor.data.repository.ThemeRepository
import com.tosin.docprocessor.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeThemeRepository(initial: ThemeMode) : ThemeRepository {
        private val state = MutableStateFlow(initial)

        override fun observeThemeMode(): Flow<ThemeMode> = state

        override suspend fun setThemeMode(mode: ThemeMode) {
            state.value = mode
        }
    }

    @Test
    fun `starts from the stored mode`() = runTest {
        val viewModel = ThemeViewModel(FakeThemeRepository(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `explicit dark choice overrides the system mode`() = runTest {
        val viewModel = ThemeViewModel(FakeThemeRepository(ThemeMode.SYSTEM))

        viewModel.setDarkMode(true)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `switching the menu toggle off selects explicit light`() = runTest {
        val viewModel = ThemeViewModel(FakeThemeRepository(ThemeMode.DARK))
        advanceUntilIdle()

        viewModel.setDarkMode(false)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun `use system theme resets the preference`() = runTest {
        val viewModel = ThemeViewModel(FakeThemeRepository(ThemeMode.LIGHT))
        advanceUntilIdle()

        viewModel.useSystemTheme()
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }
}
