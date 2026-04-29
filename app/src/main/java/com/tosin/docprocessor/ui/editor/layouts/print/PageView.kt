package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.data.common.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import kotlin.math.min

@Composable
fun PageView(
    pageModel: PageModel,
    renderer: PrintElementRenderer,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    scale: Float = 1f
) {
    val pageAspectRatio = remember(pageModel.dimensions.width, pageModel.dimensions.height) {
        pageModel.dimensions.width / pageModel.dimensions.height
    }
    val density = LocalDensity.current
    val basePageWidthPx = remember(pageModel.dimensions.width, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.width)
    }
    val basePageHeightPx = remember(pageModel.dimensions.height, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.height)
    }
    val renderBlock = remember(pageModel, renderer, scale) {
        { drawScope: androidx.compose.ui.graphics.drawscope.DrawScope ->
            with(renderer) {
                with(drawScope) {
                    val pageScale = min(
                        size.width / basePageWidthPx,
                        size.height / basePageHeightPx
                    ) * scale

                    if (pageScale != 1f) {
                        withTransform({
                            scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                        }) {
                            pageModel.elements.forEach { render(it, renderTextContent = !isEditable) }
                        }
                    } else {
                        pageModel.elements.forEach { render(it, renderTextContent = !isEditable) }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        val pageWidth = minOf(maxWidth, 760.dp)
        val pageHeight = pageWidth / pageAspectRatio
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val pageHeightPx = with(density) { pageHeight.toPx() }
        val displayScale = remember(pageWidthPx, pageHeightPx, basePageWidthPx, basePageHeightPx, scale) {
            min(pageWidthPx / basePageWidthPx, pageHeightPx / basePageHeightPx) * scale
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(pageWidth)
                    .aspectRatio(pageAspectRatio),
                color = Color.White,
                shape = RectangleShape,
                shadowElevation = 6.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderBlock(this)
                    }
                    if (isEditable) {
                        EditablePrintOverlay(
                            pageElements = pageModel.elements,
                            pageScale = displayScale,
                            unitConverter = unitConverter,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditablePrintOverlay(
    pageElements: List<PositionedElement>,
    pageScale: Float,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        pageElements.forEach { positionedElement ->
            when (val element = positionedElement.element) {
                is DocumentElement.Paragraph -> EditableParagraphBlock(
                    positionedElement = positionedElement,
                    pageScale = pageScale,
                    unitConverter = unitConverter,
                    onValueChange = { viewModel.updateParagraphById(positionedElement.elementId, it) }
                )
                is DocumentElement.SectionHeader -> EditableHeaderBlock(
                    element = element,
                    positionedElement = positionedElement,
                    pageScale = pageScale,
                    unitConverter = unitConverter,
                    onValueChange = { viewModel.updateSectionHeaderById(positionedElement.elementId, it) }
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun EditableParagraphBlock(
    positionedElement: PositionedElement,
    pageScale: Float,
    unitConverter: UnitConverter,
    onValueChange: (androidx.compose.ui.text.AnnotatedString) -> Unit
) {
    val paragraph = positionedElement.element as? DocumentElement.Paragraph ?: return
    val density = LocalDensity.current
    val xDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.left) * pageScale).toDp() }
    val yDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.top) * pageScale).toDp() }
    val widthDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.width()) * pageScale).toDp() }
    val heightDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.height()) * pageScale).toDp() }

    TextField(
        value = androidx.compose.ui.text.input.TextFieldValue(paragraph.toAnnotatedString()),
        onValueChange = { onValueChange(it.annotatedString) },
        modifier = Modifier
            .offset { IntOffset(xDp.roundToPx(), yDp.roundToPx()) }
            .width(widthDp)
            .height(heightDp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun EditableHeaderBlock(
    element: DocumentElement.SectionHeader,
    positionedElement: PositionedElement,
    pageScale: Float,
    unitConverter: UnitConverter,
    onValueChange: (String) -> Unit
) {
    val density = LocalDensity.current
    val xDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.left) * pageScale).toDp() }
    val yDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.top) * pageScale).toDp() }
    val widthDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.width()) * pageScale).toDp() }
    val textStyle = when (element.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }

    TextField(
        value = element.text,
        onValueChange = onValueChange,
        modifier = Modifier
            .offset { IntOffset(xDp.roundToPx(), yDp.roundToPx()) }
            .width(widthDp),
        textStyle = textStyle,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

private fun DocumentElement.Paragraph.toAnnotatedString(): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        listLabel?.let {
            append(it)
            append(" ")
        }
        spans.forEach { span -> append(span.text) }
    }
