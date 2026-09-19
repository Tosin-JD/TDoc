package com.tosin.docprocessor.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.data.repository.recent.RecentFilesRepository
import com.tosin.docprocessor.domain.editor.EditorDocument
import com.tosin.docprocessor.domain.editor.EditorOperations
import com.tosin.docprocessor.domain.editor.EditorTransformer
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.domain.editor.SpanOps
import com.tosin.docprocessor.domain.editor.SpanProperty
import com.tosin.docprocessor.domain.editor.UndoManager
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.isTextBlock
import com.tosin.docprocessor.ui.navigation.TDocRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val recentFilesRepository: RecentFilesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val currentUri: Uri? =
        (savedStateHandle.get<String>(TDocRoutes.ARG_URI) ?: "").takeIf { it.isNotBlank() }?.let(Uri::parse)

    private val currentMime: String =
        savedStateHandle.get<String>(TDocRoutes.ARG_MIME) ?: ""

    private val openMode: OpenMode =
        OpenMode.from(savedStateHandle.get<String>(TDocRoutes.ARG_OPEN_MODE))

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var document: EditorDocument? = null
    private val undoManager = UndoManager()

    init {
        if (currentUri != null) {
            loadDocument(currentUri)
        } else {
            activateFreshDocument()
        }
    }

    /**
     * A brand-new unnamed document: one empty editable paragraph, not dirty.
     * Used when the editor opens with no URI (launcher/deep-link with blank arg).
     */
    private fun activateFreshDocument() {
        val doc = EditorOperations.ensureAtLeastOneParagraph(
            EditorDocument(
                meta = DocumentMeta(uri = "", fileName = "Untitled", mimeType = MimeTypes.DOCX),
                blocks = emptyList(),
                nextBlockId = 1L
            )
        )
        val firstId = doc.blocks.firstOrNull { it.type.isTextBlock }?.id
        document = doc
        undoManager.clear()
        _uiState.update {
            it.copy(
                documentBlocks = doc.blocks,
                fileName = doc.meta.fileName,
                selectedBlockId = firstId,
                selection = Selection(0, 0),
                focusRequestBlockId = firstId,
                isLoading = false
            )
        }
    }

    private fun loadDocument(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                persistWritePermission(uri)
                val meta = documentRepository.getDocumentMeta(uri)
                val data = documentRepository.readDocumentFromUri(uri)
                recentFilesRepository.record(meta)
                EditorOperations.ensureAtLeastOneParagraph(
                    EditorTransformer.toEditorDocument(data.content, meta)
                )
            }
                .onSuccess { doc ->
                    document = doc
                    undoManager.clear()
                    _uiState.update {
                        it.copy(
                            documentBlocks = doc.blocks,
                            fileName = doc.meta.fileName,
                            selectedBlockId = doc.blocks.firstOrNull { block -> block.type.isTextBlock }?.id,
                            selection = Selection(0, 0),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, loadError = error.message ?: "Could not open document")
                    }
                }
        }
    }

    fun retryLoad() {
        currentUri?.let(::loadDocument)
    }

    private fun setDocument(updated: EditorDocument, dirty: Boolean) {
        document = updated
        _uiState.update {
            it.copy(
                documentBlocks = updated.blocks,
                canUndo = undoManager.canUndo,
                canRedo = undoManager.canRedo,
                isDirty = dirty
            )
        }
    }

    /** Applies a pure edit and records history, skipping no-op transitions. */
    private fun mutateBlock(
        transform: (EditorDocument, Long) -> EditorDocument
    ) {
        val blockId = _uiState.value.selectedBlockId ?: return
        val current = document ?: return
        if (current.blockAt(blockId) == null) return
        val updated = transform(current, blockId)
        if (updated.blocks == current.blocks && updated.nextBlockId == current.nextBlockId) return
        undoManager.push(current)
        setDocument(updated, dirty = true)
    }

    fun onTextChange(blockId: Long, newSpans: List<TextSpan>) {
        val current = document ?: return
        val block = current.blockAt(blockId) ?: return
        if (SpanOps.spansText(block.spans) == SpanOps.spansText(newSpans)) return
        undoManager.push(current)
        setDocument(current.replaceBlock(block.copy(spans = newSpans)), dirty = true)
    }

    fun onSelectionChange(blockId: Long, selection: Selection) {
        _uiState.update { it.copy(selectedBlockId = blockId, selection = selection) }
    }

    fun toggleFormat(property: SpanProperty) =
        mutateBlock { doc, id -> EditorOperations.toggleFormat(doc, id, _uiState.value.selection, property) }

    fun applyHeading(level: Int?) =
        mutateBlock { doc, id -> EditorOperations.setHeading(doc, id, level) }

    fun setAlignment(alignment: ParagraphAlignment) =
        mutateBlock { doc, id -> EditorOperations.setAlignment(doc, id, alignment) }

    fun toggleBulletList() =
        mutateBlock { doc, id -> EditorOperations.toggleBulletList(doc, id) }

    fun insertParagraphAfter(blockId: Long) {
        val current = document ?: return
        if (current.blockAt(blockId) == null) return
        undoManager.push(current)
        val updated = EditorOperations.insertParagraphAfter(current, blockId)
        val newIndex = updated.indexOf(blockId) + 1
        if (newIndex !in updated.blocks.indices) return
        val newId = updated.blocks[newIndex].id
        setDocument(updated, dirty = true)
        _uiState.update {
            it.copy(selectedBlockId = newId, selection = Selection(0, 0), focusRequestBlockId = newId)
        }
    }

    fun deleteBlock(blockId: Long) {
        val current = document ?: return
        if (current.blockAt(blockId) == null) return
        val previousId = previousTextBlockId(current, blockId)
        undoManager.push(current)
        val updated = EditorOperations.deleteBlock(current, blockId)
        setDocument(updated, dirty = true)
        val nextSelection = previousId
            ?: updated.blocks.firstOrNull { it.type.isTextBlock }?.id
        _uiState.update { it.copy(selectedBlockId = nextSelection) }
    }

    private fun previousTextBlockId(doc: EditorDocument, blockId: Long): Long? {
        val index = doc.indexOf(blockId)
        for (cursor in index - 1 downTo 0) {
            if (doc.blocks[cursor].type.isTextBlock) return doc.blocks[cursor].id
        }
        return null
    }

    fun undo() {
        val current = document ?: return
        undoManager.undo(current)?.let { setDocument(it, dirty = true) }
    }

    fun redo() {
        val current = document ?: return
        undoManager.redo(current)?.let { setDocument(it, dirty = true) }
    }

    // ---------------- Save flows ----------------

    fun save() {
        if (_uiState.value.isSaving) return
        when (openMode) {
            OpenMode.EDIT -> saveInPlace()
            OpenMode.VIEW, OpenMode.SEND -> requestSaveAs()
        }
    }

    private fun saveInPlace() {
        val target = currentUri
        if (target == null) {
            requestSaveAs()
        } else {
            saveToUri(target, navigateBackOnSuccess = true)
        }
    }

    fun onSaveAsResult(uri: Uri) {
        persistWritePermission(uri)
        saveToUri(uri, navigateBackOnSuccess = true)
    }

    fun onSaveAsLaunched() {
        _uiState.update { it.copy(saveAsRequest = null) }
    }

    fun cancelSaveAs() {
        _uiState.update { it.copy(saveAsRequest = null) }
    }

    private fun requestSaveAs() {
        val name = _uiState.value.fileName.ifBlank { "Untitled.docx" }
        val mime = currentMime.ifBlank { MimeTypes.DOCX }
        _uiState.update { it.copy(saveAsRequest = SaveAsRequest(name, mime)) }
    }

    private fun saveToUri(uri: Uri, navigateBackOnSuccess: Boolean) {
        val current = document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val elements = EditorTransformer.toElements(current)
                documentRepository.saveDocumentToUri(uri, elements)
                recentFilesRepository.record(documentRepository.getDocumentMeta(uri))
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isDirty = false,
                            finished = navigateBackOnSuccess,
                            message = if (navigateBackOnSuccess) null else "Saved"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, message = "Save failed: ${error.message}") }
                }
        }
    }

    private fun persistWritePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    // ---------------- Back / discard handling ----------------

    fun onBackPressed() {
        val state = _uiState.value
        if (state.isDirty && !state.isSaving) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            finishEditing()
        }
    }

    fun saveAndExit() = save()

    fun discardAndExit() {
        _uiState.update { it.copy(showDiscardDialog = false) }
        finishEditing()
    }

    fun dismissDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun finishEditing() {
        _uiState.update { it.copy(finished = true, isDirty = false) }
    }

    fun onFinishedHandled() {
        _uiState.update { it.copy(finished = false) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}