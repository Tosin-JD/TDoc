package com.tosin.docprocessor.ui.editor

import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.EditorMode
import com.tosin.docprocessor.data.common.model.ViewMode

data class EditorUiState(
    val documentData: DocumentData? = null,
    val viewMode: ViewMode = ViewMode.MOBILE,
    val editorMode: EditorMode = EditorMode.EDIT,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
