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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel
import java.io.File

@Composable
fun DocumentElementRenderer(element: DocumentElement, index: Int, viewModel: EditorViewModel) {
    when (element) {
        is DocumentElement.Paragraph -> {
            val paragraphText = remember(element.spans, element.listLabel) {
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

        DocumentElement.PageBreak -> {
            Text(text = "", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

private fun DocumentElement.Paragraph.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    listLabel?.let {
        append(it)
        append(" ")
    }
    spans.forEach { span ->
        pushStyle(span.toSpanStyle())
        append(span.text)
        pop()
    }
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
        color = parsedColor
    )
}
