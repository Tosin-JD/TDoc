package com.tosin.docprocessor.core.rendering

import android.graphics.BitmapFactory
import android.text.StaticLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PositionedElement
import com.tosin.docprocessor.data.common.model.layout.TableRenderLayout
import com.tosin.docprocessor.domain.pagination.UnitConverter
import java.io.File

class PrintElementRenderer(
    private val unitConverter: UnitConverter
) {
    fun DrawScope.render(positionedElement: PositionedElement) {
        val bounds = positionedElement.bounds
        val x = unitConverter.ptToPx(bounds.left)
        val y = unitConverter.ptToPx(bounds.top)
        val width = unitConverter.ptToPx(bounds.width())
        val height = unitConverter.ptToPx(bounds.height())

        when (val element = positionedElement.element) {
            is DocumentElement.Image -> renderImage(element, x, y, width, height)
            is DocumentElement.Table -> renderTable(element, positionedElement.layoutResult, x, y, width, height)
            else -> renderTextOrPlaceholder(positionedElement.layoutResult, x, y, width, height)
        }
    }

    private fun DrawScope.renderTextOrPlaceholder(
        layoutResult: Any?,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val layout = layoutResult as? StaticLayout
        if (layout != null) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(x, y)
                layout.draw(canvas.nativeCanvas)
                canvas.nativeCanvas.restore()
            }
            return
        }

        drawRect(
            color = Color(0x14000000),
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )
    }

    private fun DrawScope.renderImage(
        element: DocumentElement.Image,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val bitmap = File(element.sourceUri)
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }

        if (bitmap != null) {
            drawIntoCanvas { canvas ->
                val destination = android.graphics.RectF(x, y, x + width, y + height)
                canvas.nativeCanvas.drawBitmap(bitmap, null, destination, null)
            }
        } else {
            drawRect(
                color = Color(0xFFDADADA),
                topLeft = Offset(x, y),
                size = Size(width, height)
            )
            drawRect(
                color = Color(0xFF8A8A8A),
                topLeft = Offset(x, y),
                size = Size(width, height),
                style = Stroke(width = 1f)
            )
            drawLine(
                color = Color(0xFF8A8A8A),
                start = Offset(x, y),
                end = Offset(x + width, y + height),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF8A8A8A),
                start = Offset(x + width, y),
                end = Offset(x, y + height),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.renderTable(
        element: DocumentElement.Table,
        layoutResult: Any?,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val tableLayout = layoutResult as? TableRenderLayout
        if (tableLayout == null) {
            renderTableGridFallback(element, x, y, width, height)
            return
        }

        drawRect(
            color = Color.White,
            topLeft = Offset(x, y),
            size = Size(width, height)
        )
        drawRect(
            color = Color(0xFF7F7F7F),
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        var currentY = y
        val cellPaddingPx = unitConverter.ptToPx(tableLayout.cellPaddingPt)

        tableLayout.rowLayouts.forEach { rowLayout ->
            val rowHeightPx = unitConverter.ptToPx(rowLayout.heightPt)
            if (rowLayout.isHeader) {
                drawRect(
                    color = Color(0xFFF2F2F2),
                    topLeft = Offset(x, currentY),
                    size = Size(width, rowHeightPx)
                )
            }

            var currentX = x
            rowLayout.cells.forEach { cell ->
                val cellWidthPx = unitConverter.ptToPx(cell.widthPt)
                drawRect(
                    color = Color(0xFFB0B0B0),
                    topLeft = Offset(currentX, currentY),
                    size = Size(cellWidthPx, rowHeightPx),
                    style = Stroke(width = 1f)
                )

                if (cell.layout != null && cell.text.isNotBlank()) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.save()
                        canvas.nativeCanvas.translate(currentX + cellPaddingPx, currentY + cellPaddingPx)
                        cell.layout.draw(canvas.nativeCanvas)
                        canvas.nativeCanvas.restore()
                    }
                }

                currentX += cellWidthPx
            }

            currentY += rowHeightPx
        }
    }

    private fun DrawScope.renderTableGridFallback(
        element: DocumentElement.Table,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val rowCount = element.rows.size.coerceAtLeast(1)
        val columnCount = element.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val rowHeight = height / rowCount
        val columnWidth = width / columnCount

        drawRect(
            color = Color.White,
            topLeft = Offset(x, y),
            size = Size(width, height)
        )
        drawRect(
            color = Color(0xFF7F7F7F),
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        for (rowIndex in 1 until rowCount) {
            val lineY = y + (rowHeight * rowIndex)
            drawLine(
                color = Color(0xFFB0B0B0),
                start = Offset(x, lineY),
                end = Offset(x + width, lineY),
                strokeWidth = 1f
            )
        }

        for (columnIndex in 1 until columnCount) {
            val lineX = x + (columnWidth * columnIndex)
            drawLine(
                color = Color(0xFFB0B0B0),
                start = Offset(lineX, y),
                end = Offset(lineX, y + height),
                strokeWidth = 1f
            )
        }
    }
}
