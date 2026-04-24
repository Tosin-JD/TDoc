package com.tosin.docprocessor.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.model.DocumentData
import com.tosin.docprocessor.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentData?>(null)
    val currentDocument: StateFlow<DocumentData?> = _currentDocument.asStateFlow()

    fun loadDocument(filePath: String) {
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            documentRepository.loadDocument(filePath)
                .catch { exception ->
                    _uiState.value = EditorUiState.Error(exception.message ?: "Unknown error")
                }
                .collect { document ->
                    _currentDocument.value = document
                    _uiState.value = EditorUiState.Success(document)
                }
        }
    }

    fun saveDocument() {
        val document = _currentDocument.value ?: return
        viewModelScope.launch {
            _uiState.value = EditorUiState.Loading
            documentRepository.saveDocument(document)
                .catch { exception ->
                    _uiState.value = EditorUiState.Error(exception.message ?: "Unknown error")
                }
                .collect {
                    _uiState.value = EditorUiState.Success(document)
                }
        }
    }

    fun updateContent(newContent: String) {
        _currentDocument.value = _currentDocument.value?.copy(content = newContent)
    }
}
