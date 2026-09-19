package com.tosin.docprocessor.ui.home

import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.ui.editor.OpenMode

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val recents: List<DocumentMeta>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/**
 * One-shot navigation request produced by the Home screen. The NavHost consumes
 * it and clears it via [HomeViewModel.onNavigationHandled].
 */
data class HomeNavigation(
    val uri: String,
    val mime: String,
    val openMode: OpenMode,
    val saveAsName: String
)