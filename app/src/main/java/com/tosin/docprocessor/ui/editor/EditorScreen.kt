package com.tosin.docprocessor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.model.DocumentData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val uiState = viewModel.uiState.collectAsState().value
    val document = viewModel.currentDocument.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(document?.let { "Editing: ${it.filename}" } ?: "Document Editor")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (uiState) {
                is EditorUiState.Idle -> {
                    IdleContent(viewModel)
                }
                is EditorUiState.Loading -> {
                    LoadingContent()
                }
                is EditorUiState.Success -> {
                    SuccessContent(uiState.document, viewModel)
                }
                is EditorUiState.Error -> {
                    ErrorContent(uiState.message)
                }
            }
        }
    }
}

@Composable
private fun IdleContent(viewModel: EditorViewModel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Select a document to open")
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SuccessContent(document: DocumentData?, viewModel: EditorViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = viewModel.textContent,
            onValueChange = { newContent ->
                viewModel.updateContent(newContent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
            label = { Text("Document Content") }
        )
        Button(
            onClick = { viewModel.saveDocument() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Error: $message")
    }
}
