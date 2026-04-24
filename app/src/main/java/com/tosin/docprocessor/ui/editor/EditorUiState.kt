package com.tosin.docprocessor.ui.editor

import com.tosin.docprocessor.data.model.DocumentData

sealed class EditorUiState {
    object Idle : EditorUiState()
    object Loading : EditorUiState()
    data class Success(val document: DocumentData) : EditorUiState()
    data class Error(val message: String) : EditorUiState()
}
