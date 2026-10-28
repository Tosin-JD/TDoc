package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.EditorMode
import com.tosin.docprocessor.domain.model.ViewMode
import com.tosin.docprocessor.domain.model.layout.PageLayoutState
import com.tosin.docprocessor.domain.document.DiagnosticsSummary
import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.entities.RecentFile
import com.tosin.docprocessor.ui.editor.renderer.AnnotatedStringMappers
import com.tosin.docprocessor.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val recentFileDao: RecentFileDao
) : ViewModel() {

    var documentElements by mutableStateOf<List<DocumentElement>>(emptyList())
        private set

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentData?>(null)
    val currentDocument: StateFlow<DocumentData?> = _currentDocument.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _diagnostics = MutableStateFlow<List<ParseDiagnostic>>(emptyList())
    val diagnostics: StateFlow<List<ParseDiagnostic>> = _diagnostics.asStateFlow()

    private var currentUri: Uri? = null

    val isSaving: Boolean
        get() = _uiState.value.isSaving

    val isLoading: Boolean
        get() = _uiState.value.isLoading

    fun setViewMode(viewMode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = viewMode)
    }

    fun onPrintPageChanged(currentPage: Int, pageCount: Int) {
        val safePageCount = pageCount.coerceAtLeast(1)
        val pageLayout = PageLayoutState(
            pageCount = safePageCount,
            currentPage = currentPage.coerceIn(1, safePageCount)
        )
        if (_uiState.value.pageLayout != pageLayout) {
            _uiState.value = _uiState.value.copy(pageLayout = pageLayout)
        }
    }

    fun toggleEditorMode() {
        val currentMode = _uiState.value.editorMode
        _uiState.value = _uiState.value.copy(
            editorMode = if (currentMode == EditorMode.EDIT) EditorMode.PREVIEW else EditorMode.EDIT
        )
    }

    fun toggleStarred() {
        _uiState.value = _uiState.value.copy(isStarred = !_uiState.value.isStarred)
    }

    fun toggleSuggestChanges() {
        _uiState.value = _uiState.value.copy(isSuggestChangesEnabled = !_uiState.value.isSuggestChangesEnabled)
    }

    fun toggleDarkMode() {
        _uiState.value = _uiState.value.copy(isDarkMode = !_uiState.value.isDarkMode)
    }

    fun updateSearchQuery(query: String) {
        // Implement search logic if needed
    }

    fun undo() {
        // TODO: Implement undo logic
    }

    fun redo() {
        // TODO: Implement redo logic
    }

    fun onFilePicked(uri: Uri?) {
        uri?.let {
            currentUri = it
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                try {
                    val result = documentRepository.readDocumentFromUri(it)
                    val elements = result.content
                    val fileName = documentRepository.getFileName(it)
                    val document = DocumentData(
                        filename = fileName,
                        content = elements,
                        format = fileName.substringAfterLast('.', "txt")
                    )
                    recentFileDao.insertFile(RecentFile(uri = it.toString(), fileName = fileName))
                    withContext(Dispatchers.Main) {
                        documentElements = elements
                        _currentDocument.value = document
                        _diagnostics.value = result.diagnostics
                        syncDocumentState(document)
                        _uiState.value = _uiState.value.copy(
                            compatibility = result.compatibility,
                            diagnostics = DiagnosticsSummary.from(result.diagnostics),
                            outline = result.document.outline,
                            protected = result.document.settings.isProtected
                        )
                    }
                    _events.emit("Opened: $fileName")
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to load file")
                    _events.emit("Failed to open file: ${e.localizedMessage}")
                } finally {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun closeDocument() {
        currentUri = null
        documentElements = emptyList()
        _currentDocument.value = null
        _diagnostics.value = emptyList()
        _uiState.value = EditorUiState()
    }

    fun updateParagraph(index: Int, newContent: AnnotatedString) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return

        val currentList = documentElements.toMutableList()
        if (index in currentList.indices) {
            val element = currentList[index]
            if (element is DocumentElement.Paragraph) {
                currentList[index] = element.copy(
                    spans = AnnotatedStringMappers.toTextSpans(
                        annotatedString = newContent,
                        originalSpans = element.spans,
                        listLabel = element.listLabel
                    )
                )
                documentElements = currentList
                syncDocumentState()
            }
        }
    }

    fun updateSectionHeader(index: Int, newText: String) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return

        val currentList = documentElements.toMutableList()
        if (index in currentList.indices) {
            val element = currentList[index]
            if (element is DocumentElement.SectionHeader) {
                currentList[index] = element.copy(text = newText)
                documentElements = currentList
                syncDocumentState()
            }
        }
    }

    fun updateParagraphById(elementId: String, newContent: AnnotatedString) {
        val index = documentElements.indexOfFirst { it.id == elementId }
        if (index >= 0) {
            updateParagraph(index, newContent)
        }
    }

    fun updateSectionHeaderById(elementId: String, newText: String) {
        val index = documentElements.indexOfFirst { it.id == elementId }
        if (index >= 0) {
            updateSectionHeader(index, newText)
        }
    }

    fun saveCurrentFile() {
        val uri = currentUri ?: run {
            viewModelScope.launch { _events.emit("No file to save. Use Save As first.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                documentRepository.saveDocumentToUri(uri, documentElements)
                syncDocumentState()
                _events.emit("File saved successfully!")
            } catch (e: Exception) {
                _events.emit("Save failed: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun saveDocumentToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)
                documentRepository.saveDocumentToUri(uri, documentElements)
                currentUri = uri
                syncDocumentState()
                _events.emit("File saved successfully!")
            } catch (e: Exception) {
                _events.emit("Save failed: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun loadDocument(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            documentRepository.loadDocument(filePath)
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Unknown error"
                    )
                    _events.emit("Load failed: ${exception.localizedMessage}")
                }
                .collect { document ->
                    withContext(Dispatchers.Main) {
                        documentElements = document.content
                    }
                    _currentDocument.value = document
                    syncDocumentState(document)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
        }
    }

    fun saveDocument() {
        val document = _currentDocument.value?.copy(content = documentElements) ?: run {
            viewModelScope.launch { _events.emit("No document to save.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            documentRepository.saveDocument(document)
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = exception.message ?: "Unknown error"
                    )
                    _events.emit("Save failed: ${exception.localizedMessage}")
                }
                .collect {
                    syncDocumentState(document)
                    _events.emit("File saved successfully!")
                    _uiState.value = _uiState.value.copy(isSaving = false)
                }
        }
    }

    fun onFileCreated(uri: Uri?) {
        uri?.let {
            viewModelScope.launch(Dispatchers.IO) {
                documentRepository.createNewDocument(it)
                val fileName = documentRepository.getFileName(it)
                recentFileDao.insertFile(RecentFile(uri = it.toString(), fileName = fileName))
                withContext(Dispatchers.Main) {
                    currentUri = it
                    documentElements = emptyList()
                    syncDocumentState(
                        DocumentData(
                            filename = fileName,
                            content = emptyList(),
                            format = fileName.substringAfterLast('.', "txt")
                        )
                    )
                }
                _events.emit("New document created")
            }
        }
    }

    fun onSaveAs(uri: Uri?) {
        uri?.let {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    _uiState.value = _uiState.value.copy(isSaving = true)
                    documentRepository.saveDocumentToUri(it, documentElements)
                    val fileName = documentRepository.getFileName(it)
                    recentFileDao.insertFile(RecentFile(uri = it.toString(), fileName = fileName))
                    withContext(Dispatchers.Main) {
                        currentUri = it
                    }
                    _events.emit("File saved successfully!")
                } catch (e: Exception) {
                    _events.emit("Save failed: ${e.localizedMessage}")
                } finally {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                }
            }
        }
    }

    private fun syncDocumentState(document: DocumentData? = _currentDocument.value) {
        val updatedDocument = document?.copy(content = documentElements)
        _currentDocument.value = updatedDocument
        _uiState.value = _uiState.value.copy(
            documentData = updatedDocument,
            errorMessage = null
        )
    }
}
