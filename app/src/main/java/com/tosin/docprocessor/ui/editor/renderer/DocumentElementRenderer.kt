package com.tosin.docprocessor.ui.editor.renderer

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.model.DocumentElement
import com.tosin.docprocessor.ui.components.TableWidget
import com.tosin.docprocessor.ui.editor.EditorViewModel


@Composable
fun DocumentElementRenderer(element: DocumentElement, index: Int, viewModel: EditorViewModel) {
    when (element) {
        is DocumentElement.Paragraph -> {
            TextField(
                value = androidx.compose.ui.text.input.TextFieldValue(element.content),
                onValueChange = { viewModel.updateParagraph(index, it.annotatedString) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (MaterialTheme.colorScheme.surface == Color.White) Color.Black else MaterialTheme.colorScheme.onSurface
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
            val imageBitmap = remember(element.bitmap) { element.bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = element.altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentScale = ContentScale.Fit
            )
        }
        is DocumentElement.Header -> {
            Text(
                text = element.text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        is DocumentElement.Footer -> {
            Text(
                text = element.text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}