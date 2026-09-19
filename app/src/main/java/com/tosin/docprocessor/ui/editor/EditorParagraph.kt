package com.tosin.docprocessor.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.model.headingLevel
import com.tosin.docprocessor.ui.editor.AnnotatedStringMappers.toTextSpans
import com.tosin.docprocessor.ui.theme.Dimensions

/**
 * A single editable paragraph backed by the view model spans. Local
 * [TextFieldValue] keeps the IME caret/composition stable while typing; it is
 * re-synced whenever the view model publishes formatting changes.
 */
@Composable
fun EditorParagraphBlock(
    block: EditorBlock,
    focusRequestBlockId: Long?,
    onTextChange: (Long, List<TextSpan>) -> Unit,
    onSelectionChange: (Long, Selection) -> Unit,
    onEnterPressed: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val annotated = remember(block.spans) {
        AnnotatedStringMappers.spansToAnnotatedString(block.spans)
    }
    var value by remember(block.id) {
        mutableStateOf(TextFieldValue(annotated))
    }

    LaunchedEffect(annotated) {
        if (value.annotatedString != annotated || value.text != annotated.text) {
            value = value.copy(
                annotatedString = annotated,
                selection = TextRange(value.selection.start.coerceAtMost(annotated.length))
            )
        }
    }

    val focusRequester = remember(block.id) { FocusRequester() }
    LaunchedEffect(focusRequestBlockId) {
        if (focusRequestBlockId == block.id) {
            focusRequester.requestFocus()
        }
    }

    val textStyle = block.editorTextStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Dimensions.SpaceXs),
        verticalAlignment = Alignment.Top
    ) {
        if (block.listLabel != null) {
            Text(
                text = block.listLabel.trimEnd(),
                style = textStyle,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                onSelectionChange(
                    block.id,
                    Selection(newValue.selection.start, newValue.selection.end)
                )
                onTextChange(block.id, newValue.annotatedString.toTextSpans())
            },
            textStyle = textStyle,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (block.type.headingLevel != null) {
                        Modifier.semantics { heading() }
                    } else {
                        Modifier
                    }
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val ctrl = event.isCtrlPressed
                        when {
                            ctrl && event.key == Key.Z && !event.isShiftPressed -> {
                                onUndo(); true
                            }
                            ctrl && (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed)) -> {
                                onRedo(); true
                            }
                            event.key == Key.Enter && !event.isShiftPressed && !ctrl -> {
                                onEnterPressed(block.id)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
        )
    }
}

@Composable
private fun EditorBlock.editorTextStyle(): TextStyle {
    val base = when (type) {
        BlockType.HEADING1 -> MaterialTheme.typography.headlineMedium
        BlockType.HEADING2 -> MaterialTheme.typography.headlineSmall
        BlockType.HEADING3 -> MaterialTheme.typography.titleLarge
        BlockType.HEADING4 -> MaterialTheme.typography.titleMedium
        BlockType.HEADING5, BlockType.HEADING6 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }
    val textAlign = when (style.alignment) {
        ParagraphAlignment.END -> TextAlign.End
        ParagraphAlignment.CENTER -> TextAlign.Center
        ParagraphAlignment.JUSTIFIED, ParagraphAlignment.DISTRIBUTED -> TextAlign.Justify
        else -> TextAlign.Start
    }
    return base.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign
    )
}