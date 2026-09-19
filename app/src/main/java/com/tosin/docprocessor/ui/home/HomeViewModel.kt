package com.tosin.docprocessor.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.recent.RecentFilesRepository
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.ui.editor.OpenMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recentFilesRepository: RecentFilesRepository,
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigation = MutableStateFlow<HomeNavigation?>(null)
    val navigation: StateFlow<HomeNavigation?> = _navigation.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var collectJob: Job? = null

    init {
        startCollectingRecents()
    }

    /** (Re)starts the reactive recents subscription. Used by the error panel Retry. */
    fun startCollectingRecents() {
        collectJob?.cancel()
        _uiState.value = HomeUiState.Loading
        collectJob = viewModelScope.launch {
            recentFilesRepository.observeRecentFiles()
                .catch { _uiState.value = HomeUiState.Error(it.message ?: "Could not load recent files") }
                .collect { recents -> _uiState.value = HomeUiState.Loaded(recents) }
        }
    }

    fun onNavigationHandled() {
        _navigation.value = null
    }

    fun onMessageShown() {
        _message.value = null
    }

    fun removeRecent(meta: DocumentMeta) {
        viewModelScope.launch {
            runCatching { recentFilesRepository.remove(meta.uri) }
                .onFailure { _message.value = it.message ?: "Could not remove file from recents" }
        }
    }

    fun openRecent(meta: DocumentMeta) {
        viewModelScope.launch { recentFilesRepository.record(meta) }
        _navigation.value = HomeNavigation(
            uri = meta.uri,
            mime = meta.mimeType,
            openMode = OpenMode.EDIT,
            saveAsName = meta.fileName.ifBlank { "Untitled.docx" }
        )
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { documentRepository.getDocumentMeta(uri) }
                .onFailure { _message.value = it.message ?: "Could not read file" }
                .onSuccess { meta ->
                    if (!MimeTypes.isSupportedMime(meta.mimeType)) {
                        _message.value = "This file type is not supported"
                        return@launch
                    }
                    recentFilesRepository.record(meta)
                    _navigation.value = HomeNavigation(
                        uri = meta.uri,
                        mime = meta.mimeType,
                        openMode = OpenMode.VIEW,
                        saveAsName = meta.fileName.ifBlank { "Untitled.docx" }
                    )
                }
        }
    }

    fun onFileCreated(uri: Uri, defaultName: String) {
        viewModelScope.launch {
            runCatching {
                documentRepository.createNewDocument(uri, defaultName)
                recentFilesRepository.record(documentRepository.getDocumentMeta(uri))
            }
                .onFailure { _message.value = it.message ?: "Could not create file" }
                .onSuccess {
                    val meta = documentRepository.getDocumentMeta(uri)
                    _navigation.value = HomeNavigation(
                        uri = meta.uri,
                        mime = meta.mimeType,
                        openMode = OpenMode.EDIT,
                        saveAsName = meta.fileName.ifBlank { defaultName }
                    )
                }
        }
    }
}