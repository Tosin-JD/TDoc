package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.model.RenderableElement
import com.tosin.docprocessor.domain.model.SelectionState
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import kotlin.math.min

@Composable
fun CanvasPageView(
    pageModel: PageModel,
    pageCount: Int,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    scale: Float = 1f,
    isDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val unitConverter = remember(context) { UnitConverter(context) }
    val selectionState by viewModel.selectionState.collectAsState()

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        val pageWidth = minOf(maxWidth, 760.dp)
        val pageHeight = pageWidth / pageAspectRatio
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val pageHeightPx = with(density) { pageHeight.toPx() }
        val pageScale = remember(pageWidthPx, pageHeightPx, basePageWidthPx, basePageHeightPx, scale) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pageModel.elements, pageScale, isEditable) {
                            if (!isEditable) return@pointerInput
                            detectTapGestures { tapOffset ->
                                // Transform tap coordinates back to unscaled canvas coordinates
                                val canvasX = tapOffset.x / pageScale
                                val canvasY = tapOffset.y / pageScale

                                val tappedElement = pageModel.elements.find { elem ->
                                    elem.bounds.contains(canvasX, canvasY)
                                }
                                if (tappedElement != null) {
                                    viewModel.setCursor(tappedElement.elementId, 0)
                                } else {
                                    viewModel.clearCursor()
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = if (isDarkMode) Color(0xFF202124) else Color.White,
                            size = size
                        )

                        withTransform({
                            scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                        }) {
                            // Draw header elements
                            pageModel.headerElements.forEach { headerElem ->
                                headerElem.draw(
                                    drawScope = this,
                                    backgroundColor = if (isDarkMode) Color(0xFF202124) else Color.White,
                                    selectionState = selectionState,
                                    isDarkMode = isDarkMode
                                )
                            }

                            // Draw body elements
                            pageModel.elements.forEach { renderable ->
                                renderable.draw(
                                    drawScope = this,
                                    backgroundColor = if (isDarkMode) Color(0xFF202124) else Color.White,
                                    selectionState = selectionState,
                                    isDarkMode = isDarkMode
                                )
                            }

                            // Draw footer elements
                            pageModel.footerElements.forEach { footerElem ->
                                footerElem.draw(
                                    drawScope = this,
                                    backgroundColor = if (isDarkMode) Color(0xFF202124) else Color.White,
                                    selectionState = selectionState,
                                    isDarkMode = isDarkMode
                                )
                            }

                            // Draw cursor (caret) at the active element position
                            if (isEditable && selectionState.cursorElementId != null) {
                                val cursorElement = pageModel.elements.find {
                                    it.elementId == selectionState.cursorElementId
                                }
                                cursorElement?.let { elem ->
                                    val cursorHeight = elem.layout?.height?.toFloat() ?: elem.bounds.height()
                                    drawRect(
                                        color = if (isDarkMode) Color.White else Color.Black,
                                        topLeft = Offset(
                                            elem.bounds.left + 2f,
                                            elem.bounds.top + (elem.bounds.height() - cursorHeight) / 2f
                                        ),
                                        size = androidx.compose.ui.geometry.Size(2f, cursorHeight)
                                    )
                                }
                            }
                        }
                    }

                    // Page number overlay
                    Text(
                        text = "Page ${pageModel.index + 1} of $pageCount",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
