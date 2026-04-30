package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.data.common.model.EditorMode
import com.tosin.docprocessor.data.common.model.ViewMode
import com.tosin.docprocessor.ui.components.EditorMoreMenu
import com.tosin.docprocessor.ui.components.EditorToolbar
import com.tosin.docprocessor.ui.editor.layouts.mobile.MobileLayoutRenderer
import com.tosin.docprocessor.ui.editor.layouts.print.PrintLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    openDocumentUri: Uri? = null,
    onCloseRequest: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val uiState by viewModel.uiState.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    val fontSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(openDocumentUri?.toString()) {
        openDocumentUri?.let { viewModel.onFilePicked(it) }
    }

    LaunchedEffect(viewModel.documentElements.isNotEmpty(), uiState.editorMode, viewModel.activeCanvasEditorTarget) {
        if (viewModel.documentElements.isNotEmpty() && uiState.editorMode == EditorMode.EDIT) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.editorMode == EditorMode.PREVIEW) {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.documentData?.filename ?: "Untitled",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.closeDocument()
                            onCloseRequest()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comments")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            EditorMoreMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                uiState = uiState,
                                onViewModeChange = viewModel::setViewMode,
                                onToggleSuggestChanges = viewModel::toggleSuggestChanges,
                                onToggleStarred = viewModel::toggleStarred
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleEditorMode() }) {
                            Icon(Icons.Default.Check, contentDescription = "Finish Editing", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Comments")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            EditorMoreMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                uiState = uiState,
                                onViewModeChange = viewModel::setViewMode,
                                onToggleSuggestChanges = viewModel::toggleSuggestChanges,
                                onToggleStarred = viewModel::toggleStarred
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (uiState.editorMode == EditorMode.PREVIEW) {
                FloatingActionButton(
                    onClick = { viewModel.toggleEditorMode() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit document")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.viewMode) {
                    ViewMode.MOBILE -> MobileLayoutRenderer(
                        viewModel = viewModel,
                        focusRequester = focusRequester,
                        isEditable = uiState.editorMode == EditorMode.EDIT
                    )
                    ViewMode.PRINT -> PrintLayout(
                        viewModel = viewModel,
                        focusRequester = focusRequester,
                        isEditable = uiState.editorMode == EditorMode.EDIT
                    )
                }

                if (uiState.isLoading || uiState.isSaving) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (uiState.editorMode == EditorMode.EDIT) {
                    BasicTextField(
                        value = viewModel.keyboardProxyValue,
                        onValueChange = viewModel::updateKeyboardProxyValue,
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                            .focusRequester(focusRequester)
                    )
                }
            }

            if (uiState.editorMode == EditorMode.EDIT) {
                EditorToolbar(
                    onBoldClick = { },
                    onItalicClick = { },
                    onUnderlineClick = { },
                    onAlignmentClick = { },
                    onFontFeaturesClick = { showFontSheet = true },
                    modifier = Modifier.imePadding()
                )
            }
        }
    }

    if (showFontSheet) {
        FontFeaturesSheet(
            sheetState = fontSheetState,
            onDismiss = { showFontSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFeaturesSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.ime.asPaddingValues())
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Format",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Formatting tools will be here")
            }
        }
    }
}
