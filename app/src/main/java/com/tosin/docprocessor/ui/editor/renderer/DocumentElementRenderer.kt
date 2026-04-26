package com.tosin.docprocessor.ui.editor.renderer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel
import java.io.File

@Composable
fun DocumentElementRenderer(element: DocumentElement, index: Int, viewModel: EditorViewModel) {
    when (element) {
        is DocumentElement.Paragraph -> {
            val paragraphText = remember(element.spans, element.listLabel, element.style) {
                element.toAnnotatedString()
            }
            TextField(
                value = androidx.compose.ui.text.input.TextFieldValue(paragraphText),
                onValueChange = { viewModel.updateParagraph(index, it.annotatedString) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (MaterialTheme.colorScheme.surface == Color.White) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
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
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        is DocumentElement.SectionHeader -> {
            Text(
                text = element.text,
                style = when (element.level) {
                    1 -> MaterialTheme.typography.headlineSmall
                    2 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        is DocumentElement.Section -> MetadataText(element.properties.toString())

        is DocumentElement.HeaderFooter -> MetadataText(
            "${element.content.kind.name.lowercase()} (${element.content.variant}): ${element.content.text}"
        )

        is DocumentElement.Note -> MetadataText("${element.info.kind.name.lowercase()}: ${element.info.text}")

        is DocumentElement.Comment -> MetadataText("comment by ${element.info.author ?: "unknown"}: ${element.info.text}")

        is DocumentElement.Bookmark -> MetadataText("bookmark: ${element.info.name}")

        is DocumentElement.Field -> MetadataText("field ${element.info.type}: ${element.info.instruction}")

        is DocumentElement.Drawing -> MetadataText("drawing: ${element.info.kind}")

        is DocumentElement.EmbeddedObject -> MetadataText(
            "embedded object: ${element.info.programId ?: element.info.kind}"
        )

        DocumentElement.PageBreak -> {
            Text(text = "", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun MetadataText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun DocumentElement.Paragraph.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    pushStyle(style.toComposeParagraphStyle())
    listLabel?.let {
        append(it)
        append(" ")
    }
    spans.forEach { span ->
        pushStyle(span.toSpanStyle())
        append(span.text)
        pop()
    }
    pop()
}

private fun TextSpan.toSpanStyle(): SpanStyle {
    val parsedColor = runCatching {
        val hex = color.removePrefix("#")
        val argb = when (hex.length) {
            6 -> (0xFF000000 or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> Color.Black.toArgb()
        }
        Color(argb)
    }.getOrDefault(Color.Black)

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
        color = parsedColor,
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
