package com.tosin.docprocessor.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.domain.model.EditorMode
import com.tosin.docprocessor.domain.model.ViewMode
import com.tosin.docprocessor.ui.components.EditorMoreMenu
import com.tosin.docprocessor.ui.components.EditorToolbar
import com.tosin.docprocessor.ui.editor.layouts.MobileLayout
import com.tosin.docprocessor.ui.editor.layouts.print.PrintLayout
import com.tosin.docprocessor.ui.theme.LocalAppIsDarkTheme
import com.tosin.docprocessor.ui.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    openDocumentUri: Uri? = null,
    onCloseRequest: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val uiState by viewModel.uiState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    // The one resolved theme value for all document rendering in this screen.
    val isDarkTheme = LocalAppIsDarkTheme.current
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val fontSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(openDocumentUri?.toString()) {
        openDocumentUri?.let { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.recoverCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onFilePicked(uri)
        }
    }


    LaunchedEffect(viewModel.documentElements.isNotEmpty(), uiState.editorMode) {
        if (viewModel.documentElements.isNotEmpty() && uiState.editorMode == EditorMode.EDIT) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler {
        if (uiState.editorMode == EditorMode.EDIT) {
            viewModel.toggleEditorMode()
        } else {
            viewModel.closeDocument()
            onCloseRequest()
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // In edit mode the formatting toolbar owns the bottom edge and applies
        // max(navigation bar, ime) itself. Keeping the Scaffold's bottom inset here
        // would stack navigation-bar padding under the toolbar while the keyboard is
        // open. Preview mode keeps the default insets (FAB and content safe area).
        contentWindowInsets = if (uiState.editorMode == EditorMode.EDIT) {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top
            )
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
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
                        if (uiState.viewMode == ViewMode.PRINT) {
                            PageCounterPill(
                                currentPage = uiState.pageLayout.currentPage,
                                pageCount = uiState.pageLayout.pageCount
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
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
                                isDarkTheme = isDarkTheme,
                                themeMode = themeMode,
                                onSetDarkMode = themeViewModel::setDarkMode,
                                onUseSystemTheme = themeViewModel::useSystemTheme,
                                onToggleStarred = viewModel::toggleStarred
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { /* Empty title in edit mode or as needed */ },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleEditorMode() }) {
                            Icon(Icons.Default.Check, contentDescription = "Finish Editing", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        if (uiState.viewMode == ViewMode.PRINT) {
                            PageCounterPill(
                                currentPage = uiState.pageLayout.currentPage,
                                pageCount = uiState.pageLayout.pageCount
                            )
                        }
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { /* TODO: Add element */ }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        IconButton(onClick = { /* TODO */ }) {
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
                                isDarkTheme = isDarkTheme,
                                themeMode = themeMode,
                                onSetDarkMode = themeViewModel::setDarkMode,
                                onUseSystemTheme = themeViewModel::useSystemTheme,
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
                    ViewMode.MOBILE -> MobileLayout(
                        viewModel = viewModel,
                        focusRequester = focusRequester,
                        isEditable = uiState.editorMode == EditorMode.EDIT
                    )
                    ViewMode.PRINT -> PrintLayout(
                        viewModel = viewModel,
                        focusRequester = focusRequester,
                        isEditable = uiState.editorMode == EditorMode.EDIT,
                        isDarkMode = isDarkTheme
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

                if (!uiState.isLoading && !uiState.isSaving) {
                    val error = uiState.errorMessage
                    when {
                        error != null && viewModel.documentElements.isEmpty() -> DocumentStateMessage(
                            title = "This file could not be opened",
                            message = error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        uiState.documentData != null && viewModel.documentElements.isEmpty() -> DocumentStateMessage(
                            title = "Nothing to display",
                            message = "This document has no readable text content.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                if (uiState.editorMode == EditorMode.PREVIEW) {
                    DiagnosticsSummaryChip(
                        summary = uiState.diagnostics,
                        onClick = { showDiagnostics = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            }

            if (uiState.editorMode == EditorMode.EDIT) {
                EditorToolbar(
                    onBoldClick = { /* TODO */ },
                    onItalicClick = { /* TODO */ },
                    onUnderlineClick = { /* TODO */ },
                    onAlignmentClick = { /* TODO */ },
                    onFontFeaturesClick = { showFontSheet = true },
                    // Single bottom inset: the keyboard when it is visible, the
                    // navigation bar / cutout when it is not. Never both, so the
                    // toolbar sits flush on the keyboard with no gap beneath it.
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                    )
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

    if (showDiagnostics) {
        DiagnosticsSheet(
            diagnostics = diagnostics,
            sheetState = diagnosticsSheetState,
            onDismiss = { showDiagnostics = false }
        )
    }
}

/**
 * Calm, actionable content state for an empty or unreadable document. Parser
 * internals are never shown here.
 */
@Composable
private fun DocumentStateMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PageCounterPill(currentPage: Int, pageCount: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = "$currentPage / $pageCount",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
