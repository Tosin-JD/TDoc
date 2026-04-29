package com.tosin.docprocessor.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.tosin.docprocessor.ui.editor.EditorScreen
import com.tosin.docprocessor.ui.home.HomeScreen

@Composable
fun AppRoot() {
    var currentScreen by rememberSaveable { mutableStateOf("home") }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val openUri = remember(pendingUri) { pendingUri?.let { Uri.parse(it) } }

    when (currentScreen) {
        "editor" -> EditorScreen(
            openDocumentUri = openUri,
            onCloseRequest = { currentScreen = "home" }
        )
        else -> HomeScreen(
            onOpenDocument = { uri ->
                pendingUri = uri.toString()
                currentScreen = "editor"
            }
        )
    }
}
