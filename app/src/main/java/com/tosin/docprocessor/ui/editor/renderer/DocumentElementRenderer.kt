package com.tosin.docprocessor.ui.editor.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.ui.components.ImageWidget
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel

@Composable
fun DocumentElementRenderer(
    element: DocumentElement,
    index: Int,
    viewModel: EditorViewModel,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    when (element) {
        is DocumentElement.Paragraph -> {
            val textStyleMapper = remember { DocumentTextStyleMapper() }
            val baseStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
            val textModel = remember(element, baseStyle, isDarkMode) {
                textStyleMapper.map(element, baseStyle, isDarkMode)
            }
            if (isEditable) {
                TextField(
                    value = androidx.compose.ui.text.input.TextFieldValue(textModel.text),
                    onValueChange = { viewModel.updateParagraph(index, it.annotatedString) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textStyle = textModel.textStyle,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            } else {
                Text(
                    text = textModel.text,
                    style = textModel.textStyle,
                    textAlign = textModel.textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = textModel.indentStart,
                            end = textModel.indentEnd,
                            top = 4.dp + textModel.spaceBefore,
                            bottom = 4.dp + textModel.spaceAfter
                        )
                )
            }
        }

        is DocumentElement.Table -> {
            TableWidget(table = element, isDarkMode = isDarkMode)
        }

        is DocumentElement.Image -> {
            ImageWidget(
                sourceUri = element.sourceUri,
                caption = element.caption,
                altText = element.altText,
                isDarkMode = isDarkMode
            )
        }

        is DocumentElement.SectionHeader -> {
            val baseStyle = when (element.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            val textStyle = baseStyle.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isEditable) {
                TextField(
                    value = element.text,
                    onValueChange = { viewModel.updateSectionHeader(index, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textStyle = textStyle,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            } else {
                Text(
                    text = element.text,
                    style = textStyle,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        is DocumentElement.Section -> MetadataText(element.properties.toString(), isDarkMode)

        is DocumentElement.HeaderFooter -> MetadataText(
            "${element.content.kind.name.lowercase()} (${element.content.variant}): ${element.content.text}",
            isDarkMode
        )

        is DocumentElement.Note -> MetadataText("${element.info.kind.name.lowercase()}: ${element.info.text}", isDarkMode)

        is DocumentElement.Comment -> MetadataText("comment by ${element.info.author ?: "unknown"}: ${element.info.text}", isDarkMode)

        is DocumentElement.Bookmark -> MetadataText(
            "bookmark ${element.info.boundary.name.lowercase()}: ${element.info.name.ifBlank { element.info.id }}",
            isDarkMode
        )

        is DocumentElement.Field -> MetadataText("field ${element.info.type}: ${element.info.instruction}", isDarkMode)

        is DocumentElement.Metadata -> MetadataText(
            buildString {
                append(element.info.title ?: element.info.kind)
                append(": ")
                append(element.info.summary)
            },
            isDarkMode
        )

        is DocumentElement.Drawing -> MetadataText("drawing: ${element.info.kind}")

        is DocumentElement.EmbeddedObject -> UnsupportedPlaceholder(
            kind = element.info.programId ?: element.info.kind,
            description = element.info.description,
            isDarkMode = isDarkMode
        )

        is DocumentElement.PageBreak -> {
            Text(text = "", modifier = Modifier.padding(vertical = 12.dp))
        }

        is DocumentElement.TableRow -> {
            TableRowWidget(rows = listOf(element), isDarkMode = isDarkMode)
        }

        is DocumentElement.TableCell -> {
            UnsupportedPlaceholder(
                kind = "TableCell",
                description = "Cells are only rendered as part of a TableRow.",
                isDarkMode = isDarkMode
            )
        }

        else -> MetadataText("unhandled: ${element::class.simpleName}", isDarkMode)
    }
}

@Composable
private fun TableRowWidget(
    rows: List<DocumentElement.TableRow>,
    isDarkMode: Boolean
) {
    rows.forEachIndexed { rowIndex, row ->
        TableRowView(
            row = DocumentElement.TableRow(
                cells = row.cells.map { cell ->
                    DocumentElement.TableCell(
                        rich = cell.rich,
                        metadata = cell.metadata
                    )
                },
                heightPoints = row.heightPoints,
                isHeader = row.isHeader && rowIndex == 0
            ),
            columnWidthsDp = List(row.cells.size) { 100f },
            rowIndex = rowIndex,
            isDarkMode = isDarkMode
        )
    }
}

@Composable
private fun TableRowView(
    row: DocumentElement.TableRow,
    columnWidthsDp: List<Float>,
    rowIndex: Int,
    isDarkMode: Boolean
) {
    val background = when {
        row.isHeader -> Color(if (isDarkMode) 0xFF202124 else 0xFFE4E4E4)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .height(IntrinsicSize.Min)
    ) {
        row.cells.forEachIndexed { cellIndex, cell ->
            Box(
                modifier = Modifier
                    .width(columnWidthsDp.getOrElse(cellIndex) { 100f }.dp)
                    .padding(
                        horizontal = 4.dp,
                        vertical = 2.dp
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                val richText = cell.rich.spans.joinToString(" ") { it.text }
                Text(
                    text = richText.ifBlank { " " },
                    style = if (row.isHeader) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (row.isHeader) Color(if (isDarkMode) 0xFFE0E0E0 else 0xFF333333)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (cellIndex != row.cells.lastIndex) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = Color.Gray
                )
            }
        }
    }
}

/** Renders a single table row as a grid of cell widgets. */

/**
 * Neutral, non-alarming surface shown for constructs the renderer cannot draw.
 * Never hidden: it names the construct and explains that it is preserved.
 */
@Composable
private fun UnsupportedPlaceholder(
    kind: String,
    description: String?,
    isDarkMode: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkMode) 0.5f else 1f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = "Unsupported: $kind",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description ?: "Preserved in the package; not rendered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetadataText(text: String, isDarkMode: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isDarkMode) Color.LightGray else Color.Gray,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
