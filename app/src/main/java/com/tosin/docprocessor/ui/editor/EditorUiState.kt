package com.tosin.docprocessor.ui.editor

import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.EditorMode
import com.tosin.docprocessor.domain.model.ViewMode

data class EditorUiState(
    val documentData: DocumentData? = null,
    val viewMode: ViewMode = ViewMode.MOBILE,
    val editorMode: EditorMode = EditorMode.PREVIEW,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isStarred: Boolean = false,
    val isSuggestChangesEnabled: Boolean = false,
    val errorMessage: String? = null
)
