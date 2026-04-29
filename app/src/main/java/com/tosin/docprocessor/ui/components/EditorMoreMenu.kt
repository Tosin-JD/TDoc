package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.common.model.ViewMode
import com.tosin.docprocessor.ui.editor.EditorUiState

@Composable
fun EditorMoreMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    uiState: EditorUiState,
    onViewModeChange: (ViewMode) -> Unit,
    onToggleSuggestChanges: () -> Unit,
    onToggleStarred: () -> Unit,
    onDocumentOutlineClick: () -> Unit = {},
    onFindReplaceClick: () -> Unit = {},
    onWordCountClick: () -> Unit = {},
    onHorizontalLineClick: () -> Unit = {},
    onMakeCopyClick: () -> Unit = {},
    onShareExportClick: () -> Unit = {},
    onMoveClick: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("Print Layout") },
            onClick = {
                onViewModeChange(
                    if (uiState.viewMode == ViewMode.MOBILE) ViewMode.PRINT else ViewMode.MOBILE
                )
                onDismissRequest()
            },
            trailingIcon = {
                Switch(
                    checked = uiState.viewMode == ViewMode.PRINT,
                    onCheckedChange = { 
                        onViewModeChange(if (it) ViewMode.PRINT else ViewMode.MOBILE)
                        onDismissRequest()
                    }
                )
            }
        )
        DropdownMenuItem(
            text = { Text("Suggest Changes") },
            onClick = { 
                onToggleSuggestChanges()
                onDismissRequest()
            },
            trailingIcon = {
                Switch(
                    checked = uiState.isSuggestChangesEnabled,
                    onCheckedChange = { 
                        onToggleSuggestChanges()
                        onDismissRequest()
                    }
                )
            }
        )
        DropdownMenuItem(
            text = { Text("Document Outline") },
            onClick = { 
                onDocumentOutlineClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Find and replace") },
            onClick = { 
                onFindReplaceClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Word count") },
            onClick = { 
                onWordCountClick()
                onDismissRequest()
            },
            trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        DropdownMenuItem(
            text = { Text("Horizontal line") },
            onClick = { 
                onHorizontalLineClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Default.HorizontalRule, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Make a copy") },
            onClick = { 
                onMakeCopyClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Share & Export") },
            onClick = { 
                onShareExportClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Move") },
            onClick = { 
                onMoveClick()
                onDismissRequest()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
        )
        DropdownMenuItem(
            text = { Text("Star") },
            onClick = { 
                onToggleStarred()
                onDismissRequest()
            },
            trailingIcon = {
                Switch(
                    checked = uiState.isStarred,
                    onCheckedChange = { 
                        onToggleStarred()
                        onDismissRequest()
                    }
                )
            }
        )
    }
}
