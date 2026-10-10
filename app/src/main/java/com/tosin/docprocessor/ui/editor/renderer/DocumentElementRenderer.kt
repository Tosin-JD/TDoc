package com.tosin.docprocessor.ui.editor.renderer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel
import java.io.File

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
            val paragraphText = remember(element.spans, element.listLabel, element.style, isDarkMode) {
                element.toAnnotatedString(isDarkMode)
            }
            if (isEditable) {
                TextField(
                    value = androidx.compose.ui.text.input.TextFieldValue(paragraphText),
                    onValueChange = { viewModel.updateParagraph(index, it.annotatedString) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isDarkMode) Color.White else Color.Black
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            } else {
                Text(
                    text = paragraphText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (isDarkMode) Color.White else Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }

        is DocumentElement.Table -> {
            TableWidget(rows = element.rows, isDarkMode = isDarkMode)
        }

        is DocumentElement.Image -> {
            val imageBitmap = remember(element.sourceUri) {
                File(element.sourceUri)
                    .takeIf { it.exists() }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = element.altText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = element.caption ?: element.altText ?: "Image unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkMode) Color.LightGray else Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        is DocumentElement.SectionHeader -> {
            val baseStyle = when (element.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            val textStyle = baseStyle.copy(
                color = if (isDarkMode) Color.White else Color.Black
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
    }
}

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

private fun DocumentElement.Paragraph.toAnnotatedString(isDarkMode: Boolean = false): AnnotatedString = buildAnnotatedString {
    pushStyle(style.toComposeParagraphStyle())
    listLabel?.let {
        pushStyle(SpanStyle(color = if (isDarkMode) Color.White else Color.Black))
        append(it)
        append(" ")
        pop()
    }
    spans.forEach { span ->
        pushStyle(span.toSpanStyle(isDarkMode))
        append(span.text)
        pop()
    }
    pop()
}

private fun isNearBlack(color: Color): Boolean {
    // RGB luminance or distance to black
    return (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) < 0.2f
}

private fun TextSpan.toSpanStyle(isDarkMode: Boolean = false): SpanStyle {
    val parsedColor = runCatching {
        val hex = color.removePrefix("#")
        val argb = when (hex.length) {
            6 -> (0xFF000000 or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> Color.Black.toArgb()
        }
        Color(argb)
    }.getOrDefault(Color.Black)

    val finalColor = if (isDarkMode) {
        if (isNearBlack(parsedColor)) Color.White else parsedColor
    } else {
        parsedColor
    }

    return SpanStyle(
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            isUnderline && isStrikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )
            isUnderline -> TextDecoration.Underline
            isStrikethrough -> TextDecoration.LineThrough
            else -> null
        },
        color = finalColor,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified
    )
}

private fun com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle.toComposeParagraphStyle():
    ParagraphStyle = ParagraphStyle(
    textAlign = when (alignment) {
        ParagraphAlignment.END -> TextAlign.End
        ParagraphAlignment.CENTER -> TextAlign.Center
        ParagraphAlignment.JUSTIFIED -> TextAlign.Justify
        ParagraphAlignment.DISTRIBUTED -> TextAlign.Justify
        else -> TextAlign.Start
    }
)
