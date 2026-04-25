package com.tosin.docprocessor.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.data.model.MimeTypes
import com.tosin.docprocessor.data.model.ViewMode
import com.tosin.docprocessor.ui.components.FormattingToolbar
import com.tosin.docprocessor.ui.editor.layouts.MobileLayout
import com.tosin.docprocessor.ui.editor.layouts.PrintLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    var viewMode by remember { mutableStateOf(ViewMode.MOBILE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    // Listen for one-time events from the ViewModel (success/error messages)
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // This is the "Logic Bridge" to the Android System Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> viewModel.onFilePicked(uri) }
    )

    // Launcher for creating a brand-new blank file
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MimeTypes.DOCX),
        onResult = { uri -> viewModel.onFileCreated(uri) }
    )

    // Launcher for "Save As" (saving current text to a new file)
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MimeTypes.DOCX),
        onResult = { uri -> viewModel.onSaveAs(uri) }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("TDoc Editor") },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.MOBILE) ViewMode.PRINT else ViewMode.MOBILE
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.MOBILE) Icons.Default.Description else Icons.Default.Smartphone,
                            contentDescription = "Switch View"
                        )
                    }
                    IconButton(onClick = { createDocLauncher.launch("Untitled.docx") }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New")
                    }
                    IconButton(onClick = {
                        // Trigger picker for .docx and .odt
                        filePickerLauncher.launch(arrayOf(
                            MimeTypes.DOCX,
                            MimeTypes.ODT,
                            "text/plain"
                        ))
                    }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Open File")
                    }
                    IconButton(onClick = { saveAsLauncher.launch("CopyOfDoc.docx") }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Save As")
                    }
                    IconButton(
                        onClick = { viewModel.saveCurrentFile() },
                        enabled = !viewModel.isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
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
                when (viewMode) {
                    ViewMode.MOBILE -> MobileLayout(viewModel, focusRequester)
                    ViewMode.PRINT -> PrintLayout(viewModel, focusRequester)
                }

                // Loading overlay
                if (viewModel.isLoading || viewModel.isSaving) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            FormattingToolbar(
                onBoldClick = { /* TODO */ },
                onItalicClick = { /* TODO */ },
                modifier = Modifier.imePadding()
            )
                // Autofocus logic simplified for block editor
                LaunchedEffect(viewModel.documentElements.isNotEmpty()) {
                    if (viewModel.documentElements.isNotEmpty()) {
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }
