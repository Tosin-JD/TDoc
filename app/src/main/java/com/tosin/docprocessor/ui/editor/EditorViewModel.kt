package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.EditorMode
import com.tosin.docprocessor.data.common.model.ViewMode
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.entities.RecentFile
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
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

    private var currentUri: Uri? = null

    val isSaving: Boolean
        get() = _uiState.value.isSaving

    val isLoading: Boolean
        get() = _uiState.value.isLoading

    fun setViewMode(viewMode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = viewMode)
    }

    fun toggleEditorMode() {
        val currentMode = _uiState.value.editorMode
        _uiState.value = _uiState.value.copy(
            editorMode = if (currentMode == EditorMode.EDIT) EditorMode.PREVIEW else EditorMode.EDIT
        )
    }

    fun onFilePicked(uri: Uri?) {
        uri?.let {
            currentUri = it
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                try {
                    val elements = documentRepository.readDocumentFromUri(it)
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
                        syncDocumentState(document)
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

    fun updateParagraph(index: Int, newContent: AnnotatedString) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return

        val currentList = documentElements.toMutableList()
        if (index in currentList.indices) {
            val element = currentList[index]
            if (element is DocumentElement.Paragraph) {
                currentList[index] = element.copy(
                    spans = listOf(
                        TextSpan(
                            text = newContent.text,
                            color = element.spans.firstOrNull()?.color ?: "000000"
                        )
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
            viewModelScope.launch { _events.emit("No file to save. Open a file first.") }
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
