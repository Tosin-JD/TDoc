package com.tosin.docprocessor.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.tosin.docprocessor.ui.about.AboutScreen
import com.tosin.docprocessor.ui.editor.EditorScreen
import com.tosin.docprocessor.ui.help.HelpFeedbackScreen
import com.tosin.docprocessor.ui.home.HomeScreen
import com.tosin.docprocessor.ui.settings.SettingsScreen

private enum class Route { HOME, SETTINGS, ABOUT, HELP, EDITOR }

@Composable
fun AppRoot() {
    val backStack = remember { mutableStateListOf(Route.HOME) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val current = backStack.last()
    fun navigate(route: Route) { backStack.add(route) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { back() }
    when (current) {
        Route.EDITOR -> EditorScreen(
            openDocumentUri = pendingUri?.let(Uri::parse),
            onCloseRequest = { back() }
        )
        Route.SETTINGS -> SettingsScreen(onBack = { back() })
        Route.ABOUT -> AboutScreen(onBack = { back() })
        Route.HELP -> HelpFeedbackScreen(onBack = { back() })
        Route.HOME -> HomeScreen(
            onOpenDocument = { uri -> pendingUri = uri.toString(); navigate(Route.EDITOR) },
            onNavigate = { destination ->
                when (destination) {
                    "settings" -> navigate(Route.SETTINGS)
                    "about" -> navigate(Route.ABOUT)
                    "help" -> navigate(Route.HELP)
                }
            }
        )
    }
}
