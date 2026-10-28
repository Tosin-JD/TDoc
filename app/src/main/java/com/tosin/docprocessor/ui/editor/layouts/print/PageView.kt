package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.renderer.AnnotatedStringMappers.toAnnotatedString
import kotlin.math.min

@Composable
fun PageView(
    pageModel: PageModel,
    pageCount: Int,
    renderer: PrintElementRenderer,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    scale: Float = 1f,
    isDarkMode: Boolean = false
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
    val renderBlock = remember(pageModel, renderer, scale, isEditable, isDarkMode, unitConverter) {
        { drawScope: androidx.compose.ui.graphics.drawscope.DrawScope ->
            with(renderer) {
                with(drawScope) {
                    val pageScale = min(
                        size.width / basePageWidthPx,
                        size.height / basePageHeightPx
                    ) * scale

                    withTransform({
                        scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                    }) {
                        pageModel.headerElements.forEach {
                            try {
                                render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                            } catch (e: Exception) {
                            }
                        }

                        val bodyRect = pageModel.dimensions.bodyRect
                        val left = unitConverter.ptToPx(bodyRect.left)
                        val top = unitConverter.ptToPx(bodyRect.top)
                        val right = unitConverter.ptToPx(bodyRect.right)
                        val bottom = unitConverter.ptToPx(bodyRect.bottom)
                        clipRect(left, top, right, bottom) {
                            pageModel.elements.forEach {
                                try {
                                    render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                                } catch (e: Exception) {
                                    // Skip bad elements to prevent page crash
                                }
                            }
                        }

                        pageModel.footerElements.forEach {
                            try {
                                render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                            } catch (e: Exception) {
                            }
                        }
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
                color = if (isDarkMode) Color(0xFF202124) else Color.White,
                shape = RectangleShape,
                shadowElevation = 6.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderBlock(this)
                    }
                    if (isEditable) {
                        // Use new single-overlay approach (P0-2)
                        EditableTextOverlay(
                            pageElements = pageModel.elements,
                            pageScale = displayScale,
                            unitConverter = unitConverter,
                            viewModel = viewModel
                        )
                    }
                    Text(
                        text = "Page ${pageModel.index + 1} of $pageCount",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(PaddingValues(bottom = 8.dp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableParagraphBlock(
    positionedElement: PositionedElement,
    pageScale: Float,
    unitConverter: UnitConverter,
    darkMode: Boolean,
    onValueChange: (androidx.compose.ui.text.AnnotatedString) -> Unit
) {
    val paragraph = positionedElement.element as? DocumentElement.Paragraph ?: return
    val density = LocalDensity.current
    val xDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.left) * pageScale).toDp() }
    val yDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.top) * pageScale).toDp() }
    val widthDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.width()) * pageScale).toDp() }
    val heightDp = with(density) { (unitConverter.ptToPx(positionedElement.bounds.height()) * pageScale).toDp() }

    // Build the TextField textStyle from the document's run style so the editing
    // field matches the rendered text exactly (measure == draw). Font metrics are
    // taken from the paragraph's spans (which carry fontSize, bold, italic,
    // colour), while spacing/margins come from the paragraph style.
    val paragraphStyle = paragraph.style
    val sourceSpans = paragraph.spans
    val baseFontSize = sourceSpans.firstOrNull { it.fontSize != null }?.fontSize ?: 12f
    val hasBold = sourceSpans.any { it.isBold }
    val hasItalic = sourceSpans.any { it.isItalic }
    val hasUnderline = sourceSpans.any { it.isUnderline }
    val baseStyle = MaterialTheme.typography.bodyMedium
    val resolvedColor = DocumentTextColorResolver.resolveTextColor(
        sourceSpans.firstOrNull { it.color.isNotEmpty() }?.color ?: "000000",
        DocumentColorTokens.Light,
        darkMode
    )
    val textColor = androidx.compose.ui.graphics.Color(
        android.graphics.Color.red(resolvedColor) / 255f,
        android.graphics.Color.green(resolvedColor) / 255f,
        android.graphics.Color.blue(resolvedColor) / 255f,
        android.graphics.Color.alpha(resolvedColor) / 255f
    )
    val textStyle = baseStyle.copy(
        fontSize = baseFontSize.sp,
        fontWeight = if (hasBold) FontWeight.Bold else baseStyle.fontWeight,
        fontStyle = if (hasItalic) FontStyle.Italic else baseStyle.fontStyle,
        color = textColor,
        textDecoration = if (hasUnderline) TextDecoration.Underline else baseStyle.textDecoration
    )

    TextField(
        value = androidx.compose.ui.text.input.TextFieldValue(paragraph.toAnnotatedString()),
        onValueChange = { onValueChange(it.annotatedString) },
        modifier = Modifier
            .offset { IntOffset(xDp.roundToPx(), yDp.roundToPx()) }
            .width(widthDp)
            .height(heightDp),
        textStyle = textStyle,
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
