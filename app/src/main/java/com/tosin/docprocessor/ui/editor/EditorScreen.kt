package com.tosin.docprocessor.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.common.model.ViewMode
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.domain.editor.SpanOps
import com.tosin.docprocessor.domain.editor.SpanProperty
import com.tosin.docprocessor.model.headingLevel
import com.tosin.docprocessor.model.isTextBlock
import com.tosin.docprocessor.ui.components.ErrorPanel
import com.tosin.docprocessor.ui.components.FormattingToolbar
import com.tosin.docprocessor.ui.components.LoadingOverlay
import com.tosin.docprocessor.ui.editor.layouts.MobileLayout
import com.tosin.docprocessor.ui.editor.layouts.PrintLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var viewMode by remember { mutableStateOf(ViewMode.MOBILE) }

    BackHandler(enabled = true) { viewModel.onBackPressed() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            viewModel.onFinishedHandled()
            onBack()
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MimeTypes.DOCX),
        onResult = { uri -> uri?.let(viewModel::onSaveAsResult) }
    )

    LaunchedEffect(uiState.saveAsRequest) {
        uiState.saveAsRequest?.let { request ->
            saveAsLauncher.launch(request.suggestedName)
            viewModel.onSaveAsLaunched()
        }
    }

    val activeFormats = remember(uiState.selectedBlock, uiState.selection) {
        val block = uiState.selectedBlock
        if (block == null || !block.type.isTextBlock) {
            ActiveFormats()
        } else {
            ActiveFormats(
                bold = SpanOps.isPropertyActive(block.spans, uiState.selection, SpanProperty.BOLD),
                italic = SpanOps.isPropertyActive(block.spans, uiState.selection, SpanProperty.ITALIC),
                underline = SpanOps.isPropertyActive(block.spans, uiState.selection, SpanProperty.UNDERLINE),
                strikethrough = SpanOps.isPropertyActive(block.spans, uiState.selection, SpanProperty.STRIKETHROUGH),
                headingLevel = block.type.headingLevel,
                bulletList = block.listLabel != null,
                alignmentStart = block.style.alignment == ParagraphAlignment.START,
                alignmentCenter = block.style.alignment == ParagraphAlignment.CENTER,
                alignmentEnd = block.style.alignment == ParagraphAlignment.END
            )
        }
    }

    val textBlockSelected = uiState.selectedBlock?.type?.isTextBlock == true
    val enabled = !uiState.isLoading && !uiState.isSaving

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.fileName.ifBlank { "Untitled document" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.MOBILE) ViewMode.PRINT else ViewMode.MOBILE
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.MOBILE) Icons.Filled.Description else Icons.Filled.Smartphone,
                            contentDescription = "Toggle page layout"
                        )
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = enabled && uiState.isDirty) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val loadError = uiState.loadError
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    loadError != null -> ErrorPanel(
                        message = loadError,
                        onRetry = viewModel::retryLoad
                    )

                    else -> when (viewMode) {
                        ViewMode.MOBILE -> MobileLayout(
                            blocks = uiState.documentBlocks,
                            focusRequestBlockId = uiState.focusRequestBlockId,
                            onTextChange = viewModel::onTextChange,
                            onSelectionChange = viewModel::onSelectionChange,
                            onEnterPressed = viewModel::insertParagraphAfter,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo
                        )
                        ViewMode.PRINT -> PrintLayout(
                            blocks = uiState.documentBlocks,
                            focusRequestBlockId = uiState.focusRequestBlockId,
                            onTextChange = viewModel::onTextChange,
                            onSelectionChange = viewModel::onSelectionChange,
                            onEnterPressed = viewModel::insertParagraphAfter,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo
                        )
                    }
                }

                LoadingOverlay(visible = uiState.isLoading || uiState.isSaving, fullScreen = false)
            }

            FormattingToolbar(
                enabled = enabled && textBlockSelected,
                active = activeFormats,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBold = { viewModel.toggleFormat(SpanProperty.BOLD) },
                onItalic = { viewModel.toggleFormat(SpanProperty.ITALIC) },
                onUnderline = { viewModel.toggleFormat(SpanProperty.UNDERLINE) },
                onStrikethrough = { viewModel.toggleFormat(SpanProperty.STRIKETHROUGH) },
                onHeadingSelected = { viewModel.applyHeading(it) },
                onAlignStart = { viewModel.setAlignment(ParagraphAlignment.START) },
                onAlignCenter = { viewModel.setAlignment(ParagraphAlignment.CENTER) },
                onAlignEnd = { viewModel.setAlignment(ParagraphAlignment.END) },
                onBulletList = { viewModel.toggleBulletList() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                modifier = Modifier.imePadding()
            )
        }
    }

    if (uiState.showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = viewModel::discardAndExit,
            onSave = viewModel::saveAndExit,
            onCancel = viewModel::dismissDiscardDialog
        )
    }
}

@Composable
private fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Keep changes?") },
        text = { Text("This document has unsaved changes.") },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text("Discard") }
        }
    )
}