package com.tosin.docprocessor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.theme.TDocTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TDocTheme {
                EditorScreen()
            }
        }
    }
}

object MimeTypes {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val ODT = "application/vnd.oasis.opendocument.text"
}

@Composable
fun FormattingToolbar(
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onBoldClick) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
            }
            IconButton(onClick = onItalicClick) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
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

    // Launcher for creating a brand new blank file
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
                TextField(
                    value = viewModel.editorState,
                    onValueChange = { viewModel.updateContent(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Open a file or start typing...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

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
        }
    }

    // Auto-focus the text field when content is loaded
    LaunchedEffect(viewModel.editorState.annotatedString.text) {
        if (viewModel.editorState.annotatedString.text.isNotEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // FocusRequester may not be attached yet
            }
        }
    }
}