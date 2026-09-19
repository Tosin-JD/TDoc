package com.tosin.docprocessor.ui.editor

import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.EditorBlock

data class EditorUiState(
    val documentBlocks: List<EditorBlock> = emptyList(),
    val fileName: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Non-null when the last document open failed; drives the error panel. */
    val loadError: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isDirty: Boolean = false,
    val selectedBlockId: Long? = null,
    val selection: Selection = Selection(0, 0),
    /** Set when the OS back button was pressed while the document was dirty. */
    val showDiscardDialog: Boolean = false,
    /** Set when a "Save a copy" SAF create flow must be started by the UI. */
    val saveAsRequest: SaveAsRequest? = null,
    /** A block id to focus; also serves as a change counter for new paragraphs. */
    val focusRequestBlockId: Long? = null,
    /** One-shot message shown in a snackbar. */
    val message: String? = null,
    /** Set to signal the editor should close. */
    val finished: Boolean = false
) {
    val selectedBlock: EditorBlock?
        get() = documentBlocks.firstOrNull { it.id == selectedBlockId }
}

data class SaveAsRequest(
    val suggestedName: String,
    val mimeType: String
)

/** Derived format state for the toolbar, driven by the current block + selection. */
data class ActiveFormats(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val headingLevel: Int? = null,
    val bulletList: Boolean = false,
    val alignmentStart: Boolean = false,
    val alignmentCenter: Boolean = false,
    val alignmentEnd: Boolean = false
)