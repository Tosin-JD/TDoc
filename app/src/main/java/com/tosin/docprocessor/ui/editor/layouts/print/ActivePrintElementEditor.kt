package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.renderer.DocumentTextStyleMapper
import kotlin.math.roundToInt

/**
 * Dedicated, metric-matched, single-instance in-place editor for Print Layout.
 *
 * Replaces the multi-field EditablePrintOverlay. Mounts only when the user selects
 * an element to edit, applying exact pageScale to font metrics, zeroing out Compose
 * text field internal padding, and isolating list markers to prevent corruption.
 */
@Composable
fun ActivePrintElementEditor(
    positionedElement: PositionedElement,
    pageScale: Float,
    unitConverter: UnitConverter,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean,
    onCommitParagraph: (AnnotatedString) -> Unit,
    onCommitHeader: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (val element = positionedElement.element) {
        is DocumentElement.Paragraph -> {
            ActiveParagraphEditor(
                positionedElement = positionedElement,
                paragraph = element,
                pageScale = pageScale,
                unitConverter = unitConverter,
                tokens = tokens,
                isDarkMode = isDarkMode,
                onCommitText = onCommitParagraph,
                modifier = modifier
            )
        }
        is DocumentElement.SectionHeader -> {
            ActiveHeaderEditor(
                positionedElement = positionedElement,
                header = element,
                pageScale = pageScale,
                unitConverter = unitConverter,
                tokens = tokens,
                onCommitText = onCommitHeader,
                modifier = modifier
            )
        }
        else -> Unit
    }
}

@Composable
private fun ActiveParagraphEditor(
    positionedElement: PositionedElement,
    paragraph: DocumentElement.Paragraph,
    pageScale: Float,
    unitConverter: UnitConverter,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean,
    onCommitText: (AnnotatedString) -> Unit,
    modifier: Modifier = Modifier
) {
    val styleMapper = remember { DocumentTextStyleMapper() }
    val textModel = remember(paragraph, pageScale, isDarkMode) {
        styleMapper.map(
            paragraph = paragraph,
            baseStyle = TextStyle.Default,
            isDarkMode = isDarkMode,
            scale = pageScale,
            includeListLabel = false
        )
    }

    var textFieldValue by remember(positionedElement.elementId) {
        mutableStateOf(
            TextFieldValue(
                annotatedString = textModel.text,
                selection = TextRange(textModel.text.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(positionedElement.elementId) {
        focusRequester.requestFocus()
    }

    val density = LocalDensity.current
    val xOffsetPx = unitConverter.ptToPx(positionedElement.bounds.left) * pageScale
    val yOffsetPx = unitConverter.ptToPx(positionedElement.bounds.top) * pageScale
    val widthPx = unitConverter.ptToPx(positionedElement.bounds.width()) * pageScale
    val heightPx = unitConverter.ptToPx(positionedElement.bounds.height()) * pageScale
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    Box(
        modifier = modifier
            .offset { IntOffset(xOffsetPx.roundToInt(), yOffsetPx.roundToInt()) }
            .width(widthDp)
            .heightIn(min = heightDp)
    ) {
        if (paragraph.listLabel != null) {
            val labelFontSize = (12f * pageScale).sp
            Row(modifier = Modifier.matchParentSize()) {
                Text(
                    text = "${paragraph.listLabel} ",
                    style = textModel.textStyle.copy(
                        fontSize = labelFontSize,
                        color = Color(tokens.contentText)
                    ),
                    modifier = Modifier.padding(end = (4f * pageScale).dp)
                )
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onCommitText(newValue.annotatedString)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = textModel.textStyle.copy(
                        textAlign = textModel.textAlign,
                        color = Color(tokens.contentText),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Proportional,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    decorationBox = { innerTextField -> innerTextField() },
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        } else {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onCommitText(newValue.annotatedString)
                },
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester),
                textStyle = textModel.textStyle.copy(
                    textAlign = textModel.textAlign,
                    color = Color(tokens.contentText),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                decorationBox = { innerTextField -> innerTextField() },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ActiveHeaderEditor(
    positionedElement: PositionedElement,
    header: DocumentElement.SectionHeader,
    pageScale: Float,
    unitConverter: UnitConverter,
    tokens: DocumentColorTokens,
    onCommitText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(header.id) {
        mutableStateOf(
            TextFieldValue(
                text = header.text,
                selection = TextRange(header.text.length)
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(header.id) {
        focusRequester.requestFocus()
    }

    val density = LocalDensity.current
    val xOffsetPx = unitConverter.ptToPx(positionedElement.bounds.left) * pageScale
    val yOffsetPx = unitConverter.ptToPx(positionedElement.bounds.top) * pageScale
    val widthPx = unitConverter.ptToPx(positionedElement.bounds.width()) * pageScale
    val heightPx = unitConverter.ptToPx(positionedElement.bounds.height()) * pageScale
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    val baseFontSizePt = when (header.level) {
        1 -> 24f
        2 -> 18f
        else -> 14f
    }
    val scaledFontSize = (baseFontSizePt * pageScale).sp
    val scaledLineHeight = (baseFontSizePt * 1.3f * pageScale).sp

    Box(
        modifier = modifier
            .offset { IntOffset(xOffsetPx.roundToInt(), yOffsetPx.roundToInt()) }
            .width(widthDp)
            .heightIn(min = heightDp)
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onCommitText(newValue.text)
            },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontSize = scaledFontSize,
                lineHeight = scaledLineHeight,
                fontWeight = FontWeight.Bold,
                color = Color(tokens.contentText),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            decorationBox = { innerTextField -> innerTextField() },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}
