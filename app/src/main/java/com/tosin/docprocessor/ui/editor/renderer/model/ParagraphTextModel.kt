package com.tosin.docprocessor.ui.editor.renderer.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/**
 * Immutable presentation model for a paragraph. Produced by
 * `DocumentTextStyleMapper` from a `DocumentElement.Paragraph` so the composable
 * stays a dumb view (MVVM) and the mapping is testable in isolation.
 */
@Immutable
data class ParagraphTextModel(
    val text: AnnotatedString,
    val textStyle: TextStyle,
    val textAlign: TextAlign,
    val indentStart: Dp,
    val indentEnd: Dp,
    val spaceBefore: Dp,
    val spaceAfter: Dp
)
