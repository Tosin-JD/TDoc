package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.tosin.docprocessor.ui.editor.interaction.CanvasTextHitTarget
import com.tosin.docprocessor.ui.editor.interaction.EditableElementKind
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

    data class ActiveCanvasEditorTarget(
        val elementId: String,
        val kind: EditableElementKind,
        val pageIndex: Int,
        val text: String,
        val selection: TextRange
    )

    var documentElements by mutableStateOf<List<DocumentElement>>(emptyList())
        private set

    var keyboardProxyValue by mutableStateOf(TextFieldValue(""))
        private set

    var activeCanvasEditorTarget by mutableStateOf<ActiveCanvasEditorTarget?>(null)
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
        val nextMode = if (_uiState.value.editorMode == EditorMode.EDIT) {
            EditorMode.PREVIEW
        } else {
            EditorMode.EDIT
        }
        _uiState.value = _uiState.value.copy(editorMode = nextMode)
        if (nextMode == EditorMode.PREVIEW) {
            clearActiveCanvasTextEdit()
        }
    }

    fun toggleStarred() {
        _uiState.value = _uiState.value.copy(isStarred = !_uiState.value.isStarred)
    }

    fun toggleSuggestChanges() {
        _uiState.value = _uiState.value.copy(isSuggestChangesEnabled = !_uiState.value.isSuggestChangesEnabled)
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

    fun beginCanvasTextEdit(hitTarget: CanvasTextHitTarget) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return

        val selection = TextRange(hitTarget.charIndex)
        activeCanvasEditorTarget = ActiveCanvasEditorTarget(
            elementId = hitTarget.elementId,
            kind = hitTarget.kind,
            pageIndex = hitTarget.pageIndex,
            text = hitTarget.text,
            selection = selection
        )
        keyboardProxyValue = TextFieldValue(hitTarget.text, selection)
    }

    fun updateKeyboardProxyValue(newValue: TextFieldValue) {
        val activeTarget = activeCanvasEditorTarget ?: return
        keyboardProxyValue = newValue

        if (newValue.text != activeTarget.text) {
            when (activeTarget.kind) {
                EditableElementKind.PARAGRAPH -> updateParagraphTextById(activeTarget.elementId, newValue.text)
                EditableElementKind.SECTION_HEADER -> updateSectionHeaderTextById(activeTarget.elementId, newValue.text)
            }
        }

        activeCanvasEditorTarget = activeTarget.copy(
            text = newValue.text,
            selection = newValue.selection
        )
    }

    fun clearActiveCanvasTextEdit() {
        activeCanvasEditorTarget = null
        keyboardProxyValue = TextFieldValue("")
    }

    fun getEditableTextForElement(elementId: String): String? {
        return when (val element = documentElements.firstOrNull { it.id == elementId }) {
            is DocumentElement.Paragraph -> element.spans.joinToString("") { it.text }
            is DocumentElement.SectionHeader -> element.text
            else -> null
        }
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
                        clearActiveCanvasTextEdit()
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

    fun closeDocument() {
        currentUri = null
        documentElements = emptyList()
        _currentDocument.value = null
        clearActiveCanvasTextEdit()
        _uiState.value = EditorUiState()
    }

    fun updateParagraph(index: Int, newContent: AnnotatedString) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return
        val elementId = documentElements.getOrNull(index)?.id ?: return
        updateParagraphTextById(elementId, newContent.text)
    }

    fun updateSectionHeader(index: Int, newText: String) {
        if (_uiState.value.editorMode != EditorMode.EDIT) return
        val elementId = documentElements.getOrNull(index)?.id ?: return
        updateSectionHeaderTextById(elementId, newText)
    }

    fun updateParagraphById(elementId: String, newContent: AnnotatedString) {
        updateParagraphTextById(elementId, newContent.text)
    }

    fun updateSectionHeaderById(elementId: String, newText: String) {
        updateSectionHeaderTextById(elementId, newText)
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
                        clearActiveCanvasTextEdit()
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
                    clearActiveCanvasTextEdit()
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

    private fun updateParagraphTextById(elementId: String, newText: String) {
        val index = documentElements.indexOfFirst { it.id == elementId }
        if (index < 0) return

        val currentList = documentElements.toMutableList()
        val element = currentList[index] as? DocumentElement.Paragraph ?: return
        val firstSpan = element.spans.firstOrNull()
        currentList[index] = element.copy(
            spans = listOf(
                TextSpan(
                    text = newText,
                    isBold = firstSpan?.isBold ?: false,
                    isItalic = firstSpan?.isItalic ?: false,
                    isUnderline = firstSpan?.isUnderline ?: false,
                    fontFamily = firstSpan?.fontFamily,
                    fontSize = firstSpan?.fontSize,
                    color = firstSpan?.color ?: "000000"
                )
            )
        )
        documentElements = currentList
        syncDocumentState()
    }

    private fun updateSectionHeaderTextById(elementId: String, newText: String) {
        val index = documentElements.indexOfFirst { it.id == elementId }
        if (index < 0) return

        val currentList = documentElements.toMutableList()
        val element = currentList[index] as? DocumentElement.SectionHeader ?: return
        currentList[index] = element.copy(text = newText)
        documentElements = currentList
        syncDocumentState()
    }
}
