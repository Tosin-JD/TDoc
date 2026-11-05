package com.tosin.docprocessor.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * The single resolved dark-theme value for the current composition.
 *
 * Set once by [TDocTheme] and read by every document renderer, so chrome and
 * document colours can never disagree about light vs dark.
 */
val LocalAppIsDarkTheme = compositionLocalOf { false }
