package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.interaction.CanvasEditOverlay
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
        val density = LocalDensity.current
        val pageWidth = minOf(maxWidth, 760.dp)
        val displayScale = remember(pageWidth, basePageWidthPx) {
            if (basePageWidthPx > 0f) (with(density) { pageWidth.toPx() } / basePageWidthPx) * scale else scale
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .width(pageWidth)
                    .aspectRatio(pageAspectRatio),
                color = Color.White,
                shape = RectangleShape,
                shadowElevation = 6.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pageScale = min(
                            size.width / basePageWidthPx,
                            size.height / basePageHeightPx
                        ) * scale

                        with(renderer) {
                            if (pageScale != 1f) {
                                withTransform({
                                    scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                                }) {
                                    pageModel.elements.forEach { render(it, renderTextContent = true) }
                                }
                            } else {
                                pageModel.elements.forEach { render(it, renderTextContent = true) }
                            }
                        }
                    }

                    if (isEditable) {
                        CanvasEditOverlay(
                            pageElements = pageModel.elements,
                            pageScale = displayScale,
                            unitConverter = unitConverter,
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
