package com.tosin.docprocessor.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.document.DiagnosticsSummary
import com.tosin.docprocessor.domain.document.ParseDiagnostic

/**
 * Compact status chip surfacing import diagnostics. Never signals state by
 * colour alone: the label always states the counts.
 */
@Composable
fun DiagnosticsSummaryChip(
    summary: DiagnosticsSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!summary.hasIssues) return
    val label = buildString {
        if (summary.errorCount > 0) append("${summary.errorCount} error(s)")
        if (summary.warningCount > 0) {
            if (isNotEmpty()) append(" · ")
            append("${summary.warningCount} warning(s)")
        }
    }
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = if (summary.errorCount > 0) Icons.Default.Error else Icons.Default.Warning,
                contentDescription = null
            )
        },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/** Expandable list of every diagnostic with its construct and source part. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    diagnostics: List<ParseDiagnostic>,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Import report",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            if (diagnostics.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text("No issues found.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(diagnostics) { diagnostic ->
                        DiagnosticRow(diagnostic)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(diagnostic: ParseDiagnostic) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = when (diagnostic.severity) {
                ParseDiagnostic.Severity.ERROR -> Icons.Default.Error
                ParseDiagnostic.Severity.WARNING -> Icons.Default.Warning
                ParseDiagnostic.Severity.INFO -> Icons.Default.Info
            },
            contentDescription = diagnostic.severity.name,
            tint = when (diagnostic.severity) {
                ParseDiagnostic.Severity.ERROR -> MaterialTheme.colorScheme.error
                ParseDiagnostic.Severity.WARNING -> MaterialTheme.colorScheme.tertiary
                ParseDiagnostic.Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Column {
            Text(
                text = diagnostic.construct ?: "Document",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = diagnostic.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            diagnostic.elementPath?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
