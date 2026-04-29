package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.core.rendering.PrintElementRenderer

@Composable
fun PageView(
    pageModel: PageModel,
    renderer: PrintElementRenderer,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f
) {
    val widthDp = (pageModel.dimensions.width * scale).dp
    val heightDp = (pageModel.dimensions.height * scale).dp

    Card(
        modifier = modifier
            .padding(16.dp)
            .size(widthDp, heightDp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    pageModel.elements.forEach { positionedElement ->
                        renderer.render(canvas.nativeCanvas, positionedElement, scale)
                    }
                }
            }
        }
    }
}
