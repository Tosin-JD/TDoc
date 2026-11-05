package com.tosin.docprocessor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.ui.AppRoot
import com.tosin.docprocessor.ui.theme.TDocTheme
import com.tosin.docprocessor.ui.theme.ThemeViewModel
import com.tosin.docprocessor.ui.theme.resolveThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = resolveThemeMode(themeMode, systemInDarkTheme)

            TDocTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }
}
