package com.tosin.docprocessor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tosin.docprocessor.data.model.DocumentElement
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

enum class ViewMode {
    MOBILE, PRINT
}

object MimeTypes {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val ODT = "application/vnd.oasis.opendocument.text"
}

@Composable
fun TableWidget(rows: List<List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, Color.Gray)
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cellText ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(0.5.dp, Color.LightGray)
                            .padding(8.dp)
                    ) {
                        Text(text = cellText, style = MaterialTheme.typography.bodySmall, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentElementRenderer(element: DocumentElement, index: Int, viewModel: EditorViewModel) {
    when (element) {
        is DocumentElement.Paragraph -> {
            TextField(
                value = androidx.compose.ui.text.input.TextFieldValue(element.content),
                onValueChange = { viewModel.updateParagraph(index, it.annotatedString) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (MaterialTheme.colorScheme.surface == Color.White) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        is DocumentElement.Table -> {
            TableWidget(rows = element.rows)
        }
        is DocumentElement.Image -> {
            val imageBitmap = remember(element.bitmap) { element.bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = element.altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentScale = ContentScale.Fit
            )
        }
        is DocumentElement.Header -> {
            Text(
                text = element.text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        is DocumentElement.Footer -> {
            Text(
                text = element.text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun MobileLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(focusRequester)
    ) {
        itemsIndexed(viewModel.documentElements) { index, element ->
            DocumentElementRenderer(element, index, viewModel)
        }
    }
}

@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester
) {
    val pageWidth = 595.dp
    val pageMinHeight = 842.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .width(pageWidth)
                .shadow(8.dp)
                .background(Color.White)
                .defaultMinSize(minHeight = pageMinHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 72.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                viewModel.documentElements.forEachIndexed { index, element ->
                    DocumentElementRenderer(element, index, viewModel)
                }
            }
        }
    }
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
                // Auto-focus logic simplified for block editor
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





