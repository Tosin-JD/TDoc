// path/to/File.kt
package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.renderer.AnnotatedStringMappers.toAnnotatedString

/**
 * Single editable overlay using BasicTextField
 * P0-2: Replaces per-paragraph TextField approach
 */
@Composable
fun EditableTextOverlay(
    pageElements: List<PositionedElement>,
    pageScale: Float,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    // Build combined annotated string from all editable elements
    val combinedText = remember(pageElements) {
        buildCombinedAnnotatedString(pageElements)
    }
    
    val textFieldValue = remember { mutableStateOf(TextFieldValue(combinedText)) }
    
    Box(modifier = modifier.fillMaxSize()) {
        BasicTextField(
            value = textFieldValue.value,
            onValueChange = { textFieldValue.value = it },
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = Color.Transparent // Text drawn by canvas, overlay for input only
            ),
            cursorBrush = SolidColor(Color.Black)
        )
    }
}

private fun buildCombinedAnnotatedString(pageElements: List<PositionedElement>): AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        pageElements.forEachIndexed { index, positionedElement ->
            when (val element = positionedElement.element) {
                is DocumentElement.Paragraph -> {
                    append(element.toAnnotatedString())
                }
                is DocumentElement.SectionHeader -> {
                    append(element.text)
                }
                else -> {
                    // Skip other elements for now
                }
            }
            if (index < pageElements.size - 1) {
                append("\n")
            }
        }
    }
}
