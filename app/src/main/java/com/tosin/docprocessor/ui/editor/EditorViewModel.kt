package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.model.DocumentData
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
    private val documentRepository: DocumentRepository
) : ViewModel() {

    var editorState by mutableStateOf(TextFieldValue(AnnotatedString("")))
        private set

    var isSaving by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentData?>(null)
    val currentDocument: StateFlow<DocumentData?> = _currentDocument.asStateFlow()

    // One-time event channel for Snackbar messages
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var currentUri: Uri? = null

    fun onFilePicked(uri: Uri?) {
        uri?.let {
            currentUri = it
            viewModelScope.launch(Dispatchers.IO) {
                isLoading = true
                _uiState.value = EditorUiState.Loading
                try {
                    val text = documentRepository.readTextFromUri(it)
                    val fileName = documentRepository.getFileName(it)
                    val document = DocumentData(
                        filename = fileName,
                        content = text,
                        format = fileName.substringAfterLast('.', "txt")
                    )
                    withContext(Dispatchers.Main) {
                        editorState = TextFieldValue(text)
                        _currentDocument.value = document
                        _uiState.value = EditorUiState.Success(document)
                    }
                    _events.emit("Opened: $fileName")
                } catch (e: Exception) {
                    _uiState.value = EditorUiState.Error(e.message ?: "Failed to load file")
                    _events.emit("Failed to open file: ${e.localizedMessage}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun updateContent(newState: TextFieldValue) {
        editorState = newState
    }

    fun saveCurrentFile() {
        val uri = currentUri ?: run {
            viewModelScope.launch { _events.emit("No file to save. Open a file first.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isSaving = true
                documentRepository.saveTextToUri(uri, editorState.annotatedString)
                // Update the document state with latest content
                _currentDocument.value = _currentDocument.value?.copy(content = editorState.annotatedString)
                _events.emit("File saved successfully!")
            } catch (e: Exception) {
                _events.emit("Save failed: ${e.localizedMessage}")
            } finally {
                isSaving = false
            }
        }
    }

    fun loadDocument(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = EditorUiState.Loading
            isLoading = true
            documentRepository.loadDocument(filePath)
                .catch { exception ->
                    _uiState.value = EditorUiState.Error(exception.message ?: "Unknown error")
                    _events.emit("Load failed: ${exception.localizedMessage}")
                    isLoading = false
                }
                .collect { document ->
                    withContext(Dispatchers.Main) {
                        editorState = TextFieldValue(document.content)
                    }
                    _currentDocument.value = document
                    _uiState.value = EditorUiState.Success(document)
                    isLoading = false
                }
        }
    }

    // Legacy save via Flow (for file-path-based documents)
    fun saveDocument() {
        val document = _currentDocument.value?.copy(content = editorState.annotatedString) ?: run {
            viewModelScope.launch { _events.emit("No document to save.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isSaving = true
            _uiState.value = EditorUiState.Loading
            documentRepository.saveDocument(document)
                .catch { exception ->
                    _uiState.value = EditorUiState.Error(exception.message ?: "Unknown error")
                    _events.emit("Save failed: ${exception.localizedMessage}")
                    isSaving = false
                }
                .collect {
                    _uiState.value = EditorUiState.Success(document)
                    _events.emit("File saved successfully!")
                    isSaving = false
                }
        }
    }
}
