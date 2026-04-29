package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorToolbar(
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onAlignmentClick: (String) -> Unit,
    onFontFeaturesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAlignmentMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBoldClick) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold")
            }
            IconButton(onClick = onItalicClick) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
            }
            IconButton(onClick = onUnderlineClick) {
                Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline")
            }
            
            Box {
                IconButton(onClick = { showAlignmentMenu = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatAlignLeft, contentDescription = "Alignment")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(
                    expanded = showAlignmentMenu,
                    onDismissRequest = { showAlignmentMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left") },
                        onClick = { onAlignmentClick("Left"); showAlignmentMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center") },
                        onClick = { onAlignmentClick("Center"); showAlignmentMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Icon(Icons.Default.FormatAlignRight, contentDescription = "Align Right") },
                        onClick = { onAlignmentClick("Right"); showAlignmentMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Icon(Icons.Default.FormatAlignJustify, contentDescription = "Align Justify") },
                        onClick = { onAlignmentClick("Justify"); showAlignmentMenu = false }
                    )
                }
            }

            IconButton(onClick = onFontFeaturesClick) {
                Icon(Icons.Default.FormatColorText, contentDescription = "Font Features")
            }
        }
    }
}
