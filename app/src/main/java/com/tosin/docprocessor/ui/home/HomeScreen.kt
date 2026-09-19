package com.tosin.docprocessor.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.ui.components.ErrorPanel
import com.tosin.docprocessor.ui.components.LoadingOverlay
import com.tosin.docprocessor.ui.theme.Dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (HomeNavigation) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { text ->
            snackbarHostState.showSnackbar(text)
            viewModel.onMessageShown()
        }
    }

    LaunchedEffect(navigation) {
        navigation?.let { nav ->
            onOpenDocument(nav)
            viewModel.onNavigationHandled()
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::onFilePicked) }
    )

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MimeTypes.DOCX),
        onResult = { uri -> uri?.let { viewModel.onFileCreated(it, "Untitled.docx") } }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("TDoc") },
                actions = {
                    IconButton(onClick = { createDocLauncher.launch("Untitled.docx") }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "Create new document"
                        )
                    }
                    IconButton(onClick = { openFileLauncher.launch(MimeTypes.supportedMimes()) }) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = "Open document"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                HomeUiState.Loading -> LoadingOverlay(visible = true, fullScreen = true)

                is HomeUiState.Error -> ErrorPanel(
                    message = state.message,
                    onRetry = viewModel::startCollectingRecents
                )

                is HomeUiState.Loaded -> {
                    if (state.recents.isEmpty()) {
                        EmptyState(
                            onCreateClick = { createDocLauncher.launch("Untitled.docx") }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Dimensions.SpaceMd),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.SpaceSm)
                        ) {
                            items(state.recents, key = { it.uri }) { meta ->
                                RecentFileCard(
                                    meta = meta,
                                    onClick = { viewModel.openRecent(meta) },
                                    onRemove = { viewModel.removeRecent(meta) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}