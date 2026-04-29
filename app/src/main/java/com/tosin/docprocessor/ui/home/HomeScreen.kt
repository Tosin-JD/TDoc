package com.tosin.docprocessor.ui.home

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenDocument: (Uri) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var selectedDocument by remember { mutableStateOf<HomeDocumentItem?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sortSheetState = rememberModalBottomSheetState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Browse",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
                DrawerEntry("Recent", Icons.Default.AccessTime)
                DrawerEntry("Starred", Icons.Default.Star)
                DrawerEntry("Documents", Icons.Default.Description)
                DrawerEntry("Download", Icons.Default.Download)
                DrawerEntry("Internal Storage", Icons.Default.Storage)
                DrawerEntry("SD Card", Icons.Default.Storage)
                DrawerEntry("OTG", Icons.Default.Usb)
                DrawerEntry("Others", Icons.Default.Folder)
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                DrawerEntry("Settings", Icons.Default.Settings)
                DrawerEntry("About", Icons.Default.Info)
                DrawerEntry("Help & Feedback", Icons.AutoMirrored.Filled.Help)
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Browse locations")
                    }
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        singleLine = true,
                        placeholder = { Text("Search documents") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort by",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    AssistChip(
                        onClick = { showSortSheet = true },
                        label = {
                            Text(
                                when (uiState.sortOption) {
                                    DocumentSortOption.LAST_MODIFIED -> "Last modified"
                                    DocumentSortOption.LAST_OPENED -> "Last opened"
                                    DocumentSortOption.NAME -> "Name"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::toggleLayoutMode) {
                        Icon(
                            imageVector = if (uiState.layoutMode == DocumentLayoutMode.LIST) {
                                Icons.Default.GridView
                            } else {
                                Icons.Default.TableRows
                            },
                            contentDescription = "Toggle layout"
                        )
                    }
                }

                if (uiState.documents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent DOCX or ODT files yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (uiState.layoutMode == DocumentLayoutMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.documents, key = { it.uri }) { item ->
                            DocumentListItem(
                                item = item,
                                onClick = { onOpenDocument(Uri.parse(item.uri)) },
                                onMenuClick = { selectedDocument = item }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.documents, key = { it.uri }) { item ->
                            DocumentGridItem(
                                item = item,
                                onClick = { onOpenDocument(Uri.parse(item.uri)) },
                                onMenuClick = { selectedDocument = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedDocument != null) {
        val item = selectedDocument!!
        ModalBottomSheet(
            onDismissRequest = { selectedDocument = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = item.mimeType.ifBlank { "Document" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SheetAction("Open", Icons.Default.OpenInNew) {
                    selectedDocument = null
                    onOpenDocument(Uri.parse(item.uri))
                }
                SheetAction(
                    if (item.isStarred) "Remove from starred" else "Add to starred",
                    if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder
                ) {
                    viewModel.toggleStarred(item.uri)
                }
                SheetAction("Share", Icons.Default.Share) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType.ifBlank { "*/*" }
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(context, Intent.createChooser(shareIntent, "Share document"), null)
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                SheetAction("Details", Icons.Default.Info) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            buildString {
                                append(item.name)
                                item.lastModified?.let { append(" • Modified ${formatDate(it)}") }
                            }
                        )
                    }
                }
                SheetAction("Export", Icons.Default.Description) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Export is coming soon.") }
                }
                SheetAction("Make a copy", Icons.Default.Description) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Make a copy is coming soon.") }
                }
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                SheetAction("Rename", Icons.Default.SortByAlpha) {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Rename is coming soon.") }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sortSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                SortOptionItem(
                    label = "Last opened",
                    selected = uiState.sortOption == DocumentSortOption.LAST_OPENED,
                    onClick = {
                        viewModel.updateSortOption(DocumentSortOption.LAST_OPENED)
                        showSortSheet = false
                    }
                )
                SortOptionItem(
                    label = "Last modified",
                    selected = uiState.sortOption == DocumentSortOption.LAST_MODIFIED,
                    onClick = {
                        viewModel.updateSortOption(DocumentSortOption.LAST_MODIFIED)
                        showSortSheet = false
                    }
                )
                SortOptionItem(
                    label = "Name",
                    selected = uiState.sortOption == DocumentSortOption.NAME,
                    onClick = {
                        viewModel.updateSortOption(DocumentSortOption.NAME)
                        showSortSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SortOptionItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null
    )
}

@Composable
private fun DrawerEntry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}


@Composable
private fun DocumentListItem(
    item: HomeDocumentItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    text = "Last opened ${formatDate(item.lastOpened)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Document options")
            }
        }
    }
}

@Composable
private fun DocumentGridItem(
    item: HomeDocumentItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Document options")
                }
            }
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = formatDate(item.lastOpened),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}

private fun formatDate(timestamp: Long): String =
    DateFormat.format("MMM d, yyyy", Date(timestamp)).toString()
