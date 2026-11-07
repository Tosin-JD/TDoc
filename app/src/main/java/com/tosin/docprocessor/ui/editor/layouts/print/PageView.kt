package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
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
    isDarkMode: Boolean = false
) {
    val pageAspectRatio = remember(pageModel.dimensions.width, pageModel.dimensions.height) {
        pageModel.dimensions.width / pageModel.dimensions.height
    }
    val density = LocalDensity.current
    val basePageWidthPx = remember(pageModel.dimensions.width, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.width)
    }
    val pxPerPt = remember(unitConverter) { unitConverter.ptToPx(1f) }

    val uiState by viewModel.uiState.collectAsState()
    val activeEditingElementId = uiState.activeEditingElementId

    // Page, label and edit-field colours come from the same resolved theme as
    // the chrome; no hardcoded black/white in the page surface.
    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
    // Document-authored page fill (`w:background`/ODF) resolved for the
    // current theme; absent shading keeps the theme page colour.
    val resolvedPageColor = DocumentTextColorResolver.resolveBackgroundColor(
        pageModel.dimensions.pageBackground, tokens, isDarkMode, tokens.pageBackground
    )?.let { Color(it) }
        ?: Color(tokens.pageBackground)
    val pageColor by animateColorAsState(
        targetValue = resolvedPageColor,
        label = "print-page"
    )
    val pageBorderColor = DocumentTextColorResolver.resolveBorderColor(
        pageModel.dimensions.pageBorderColor, tokens, isDarkMode,
        resolvedPageColor.toArgb()
    ).let { Color(it) }
        .takeIf { pageModel.dimensions.pageBorderColor != null }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        val pageWidth = minOf(maxWidth, 760.dp)
        val pageHeight = pageWidth / pageAspectRatio
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val pageHeightPx = with(density) { pageHeight.toPx() }
        val basePageHeightPx = unitConverter.ptToPx(pageModel.dimensions.height)
        val displayScale = remember(pageWidthPx, pageHeightPx, basePageWidthPx, basePageHeightPx) {
            min(pageWidthPx / basePageWidthPx, pageHeightPx / basePageHeightPx)
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(pageWidth)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageAspectRatio)
                        .then(
                            pageBorderColor?.let {
                                Modifier.border(1.dp, it, RectangleShape)
                            } ?: Modifier
                        ),
                    color = pageColor,
                    shape = RectangleShape,
                    shadowElevation = 6.dp
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isEditable, pageModel, displayScale) {
                            if (!isEditable) return@pointerInput
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val tapPtX = (tapOffset.x / displayScale) / pxPerPt
                                    val tapPtY = (tapOffset.y / displayScale) / pxPerPt
                                    val hit = pageModel.elements.find { elem ->
                                        (elem.element is DocumentElement.Paragraph || elem.element is DocumentElement.SectionHeader) &&
                                            elem.bounds.contains(tapPtX, tapPtY)
                                    }
                                    if (hit != null) {
                                        viewModel.setActiveEditingElement(hit.elementId)
                                    } else {
                                        viewModel.setActiveEditingElement(null)
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        withTransform({
                            scale(scaleX = displayScale, scaleY = displayScale, pivot = Offset.Zero)
                        }) {
                            with(renderer) {
                                pageModel.headerElements.forEach { positionedElement ->
                                    render(
                                        positionedElement = positionedElement,
                                        activeEditingElementId = activeEditingElementId,
                                        isDarkMode = isDarkMode
                                    )
                                }

                                val bodyRect = pageModel.dimensions.bodyRect
                                clipRect(
                                    unitConverter.ptToPx(bodyRect.left),
                                    unitConverter.ptToPx(bodyRect.top),
                                    unitConverter.ptToPx(bodyRect.right),
                                    unitConverter.ptToPx(bodyRect.bottom)
                                ) {
                                    pageModel.elements.forEach { positionedElement ->
                                        render(
                                            positionedElement = positionedElement,
                                            activeEditingElementId = activeEditingElementId,
                                            isDarkMode = isDarkMode
                                        )
                                    }
                                }

                                pageModel.footerElements.forEach { positionedElement ->
                                    render(
                                        positionedElement = positionedElement,
                                        activeEditingElementId = activeEditingElementId,
                                        isDarkMode = isDarkMode
                                    )
                                }
                            }
                        }
                    }

                    if (isEditable && activeEditingElementId != null) {
                        val activeElement = pageModel.elements.find {
                            it.elementId == activeEditingElementId || it.logicalElementId == activeEditingElementId
                        }
                        if (activeElement != null) {
                            ActivePrintElementEditor(
                                positionedElement = activeElement,
                                pageScale = displayScale,
                                unitConverter = unitConverter,
                                tokens = tokens,
                                isDarkMode = isDarkMode,
                                onCommitParagraph = { newContent ->
                                    viewModel.updateParagraphFragment(
                                        logicalId = activeElement.logicalElementId,
                                        fragmentIndex = activeElement.fragmentIndex,
                                        isFragment = activeElement.isFragment,
                                        charStart = activeElement.charStart,
                                        charEnd = activeElement.charEnd,
                                        updatedFragmentContent = newContent
                                    )
                                },
                                onCommitHeader = { newText ->
                                    viewModel.updateSectionHeaderById(activeElement.elementId, newText)
                                }
                            )
                        }
                    }
                    }
                }

                Text(
                    text = "Page ${pageModel.index + 1} of $pageCount",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(tokens.secondaryText)
                )
            }
        }
    }
}
