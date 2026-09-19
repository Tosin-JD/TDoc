package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.ui.editor.ActiveFormats

@Composable
fun FormattingToolbar(
    enabled: Boolean,
    active: ActiveFormats,
    canUndo: Boolean,
    canRedo: Boolean,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrikethrough: () -> Unit,
    onHeadingSelected: (Int?) -> Unit,
    onAlignStart: () -> Unit,
    onAlignCenter: () -> Unit,
    onAlignEnd: () -> Unit,
    onBulletList: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                ToggleButton(
                    icon = Icons.Filled.FormatBold,
                    contentDescription = "Bold",
                    active = active.bold,
                    enabled = enabled,
                    onClick = onBold
                )
            }
            item { ToolbarDivider() }
            item { ToggleButton(Icons.Filled.FormatItalic, "Italic", active.italic, enabled, onItalic) }
            item { ToggleButton(Icons.Filled.FormatUnderlined, "Underline", active.underline, enabled, onUnderline) }
            item { ToggleButton(Icons.Filled.FormatStrikethrough, "Strikethrough", active.strikethrough, enabled, onStrikethrough) }
            item { ToolbarDivider() }
            item {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = enabled
                ) {
                    Icon(
                        imageVector = Icons.Filled.Title,
                        contentDescription = "Styles",
                        tint = if (active.headingLevel != null) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Body text") },
                        onClick = { onHeadingSelected(null); menuOpen = false }
                    )
                    (1..6).forEach { level ->
                        DropdownMenuItem(
                            text = { Text("Heading $level") },
                            onClick = { onHeadingSelected(level); menuOpen = false }
                        )
                    }
                }
            }
            item { ToolbarDivider() }
            item {
                ToggleButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "Align left", active.alignmentStart, enabled, onAlignStart)
            }
            item {
                ToggleButton(Icons.Filled.FormatAlignCenter, "Align center", active.alignmentCenter, enabled, onAlignCenter)
            }
            item {
                ToggleButton(Icons.AutoMirrored.Filled.FormatAlignRight, "Align right", active.alignmentEnd, enabled, onAlignEnd)
            }
            item { ToolbarDivider() }
            item {
                ToggleButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet list", active.bulletList, enabled, onBulletList)
            }
            item { ToolbarDivider() }
            item {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
            item {
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(
        modifier = Modifier.height(24.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ToggleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}