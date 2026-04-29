package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.UnitConverter

@Composable
fun PageView(
    pageModel: PageModel,
    renderer: PrintElementRenderer,
    unitConverter: UnitConverter,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val density = LocalDensity.current
    val pageWidthDp = with(density) { (unitConverter.ptToPx(pageModel.dimensions.width) * scale).toDp() }
    val pageHeightDp = with(density) { (unitConverter.ptToPx(pageModel.dimensions.height) * scale).toDp() }
    val renderBlock = remember(pageModel, renderer, scale) {
        { drawScope: androidx.compose.ui.graphics.drawscope.DrawScope ->
            with(renderer) {
                with(drawScope) {
                    if (scale != 1f) {
                        withTransform({
                            scale(scaleX = scale, scaleY = scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                        }) {
                            pageModel.elements.forEach { render(it) }
                        }
                    } else {
                        pageModel.elements.forEach { render(it) }
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .size(pageWidthDp, pageHeightDp),
        color = Color.White,
        shape = RectangleShape,
        shadowElevation = 6.dp
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            renderBlock(this)
        }
    }
}
