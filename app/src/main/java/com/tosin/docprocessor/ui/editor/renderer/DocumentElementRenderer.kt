package com.tosin.docprocessor.ui.editor.renderer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.components.ImageWidget
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.theme.LocalAppIsDarkTheme

/**
 * Maps one body [DocumentElement] to Compose for the mobile reading surface.
 *
 * Which elements reach this composable is decided by `MobileContentPolicy`;
 * section setup, page breaks, header/footer stories and metadata records are
 * never body text here. Colours come from the one resolved app theme.
 */
@Composable
fun DocumentElementRenderer(
    element: DocumentElement,
    index: Int,
    viewModel: EditorViewModel,
    isEditable: Boolean
) {
    val isDarkMode = LocalAppIsDarkTheme.current
    when (element) {
        is DocumentElement.Paragraph -> {
            val textStyleMapper = remember { DocumentTextStyleMapper() }
            val baseStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
            val textModel = remember(element, baseStyle, isDarkMode) {
                textStyleMapper.map(element, baseStyle, isDarkMode)
            }
            var fieldValue by remember(element.id) {
                mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(textModel.text))
            }
            LaunchedEffect(textModel.text) {
                if (fieldValue.annotatedString != textModel.text) {
                    fieldValue = fieldValue.copy(annotatedString = textModel.text)
                }
            }
            if (isEditable) {
                TextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        viewModel.updateParagraph(index, it.annotatedString)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = textModel.indentStart,
                            end = textModel.indentEnd,
                            top = 4.dp + textModel.spaceBefore,
                            bottom = 4.dp + textModel.spaceAfter
                        ),
                    textStyle = textModel.textStyle.copy(textAlign = textModel.textAlign),
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
            TableWidget(table = element)
        }

        is DocumentElement.Image -> {
            ImageWidget(
                sourceUri = element.sourceUri,
                caption = element.caption,
                altText = element.altText
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
            // Expose heading levels to assistive technology so long documents
            // can be navigated by structure.
            val headingModifier = Modifier.semantics { heading() }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .then(headingModifier)
                )
            }
        }

        // Control data and metadata records: deliberately not rendered as text.
        is DocumentElement.Section,
        is DocumentElement.HeaderFooter,
        is DocumentElement.Comment,
        is DocumentElement.Bookmark,
        is DocumentElement.Field,
        is DocumentElement.Metadata,
        is DocumentElement.Note,
        is DocumentElement.PageBreak -> Unit

        is DocumentElement.Drawing -> UnsupportedPlaceholder(
            kind = element.info.kind,
            description = "This drawing is preserved but cannot be displayed in mobile view."
        )

        is DocumentElement.EmbeddedObject -> UnsupportedPlaceholder(
            kind = element.info.programId ?: element.info.kind,
            description = element.info.description
        )
    }
}

/**
 * Neutral, non-alarming surface shown for constructs the renderer cannot draw.
 * Never hidden: it names the construct and explains that it is preserved.
 */
@Composable
private fun UnsupportedPlaceholder(
    kind: String,
    description: String?
) {
    val isDarkMode = LocalAppIsDarkTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (isDarkMode) 0.9f else 1f
        ),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
