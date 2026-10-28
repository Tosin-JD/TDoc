package com.tosin.docprocessor.ui.editor

import com.tosin.docprocessor.domain.document.CompatibilityLevel
import com.tosin.docprocessor.domain.document.DiagnosticsSummary
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.EditorMode
import com.tosin.docprocessor.domain.model.ViewMode
import com.tosin.docprocessor.domain.document.model.OutlineEntry
import com.tosin.docprocessor.domain.model.layout.PageLayoutState

data class EditorUiState(
    val documentData: DocumentData? = null,
    val viewMode: ViewMode = ViewMode.MOBILE,
    val editorMode: EditorMode = EditorMode.PREVIEW,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isStarred: Boolean = false,
    val isSuggestChangesEnabled: Boolean = false,
    val isDarkMode: Boolean = false,
    val errorMessage: String? = null,
    val compatibility: CompatibilityLevel? = null,
    val diagnostics: DiagnosticsSummary = DiagnosticsSummary(),
    val outline: List<OutlineEntry> = emptyList(),
    val protected: Boolean = false,
    val pageLayout: PageLayoutState = PageLayoutState()
)
