package com.tosin.docprocessor.core.rendering

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.LayoutResult
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import java.io.File

class PrintElementRenderer(
    private val unitConverter: UnitConverter
) {
    fun DrawScope.render(
        positionedElement: PositionedElement,
        renderTextContent: Boolean = true,
        isDarkMode: Boolean = false
    ) {
        val bounds = positionedElement.bounds
        val x = unitConverter.ptToPx(bounds.left)
        val y = unitConverter.ptToPx(bounds.top)
        val width = unitConverter.ptToPx(bounds.width())
        val height = unitConverter.ptToPx(bounds.height())
        val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light

        when (val element = positionedElement.element) {
            is DocumentElement.Image ->
                renderImage(element, positionedElement.layoutResult, x, y, width, height, tokens)
            is DocumentElement.Table ->
                renderTable(element, positionedElement.layoutResult, x, y, width, height, tokens)
            is DocumentElement.HeaderFooter -> {
                if (renderTextContent) {
                    renderHeaderFooter(element, positionedElement.layoutResult, x, y, width, height, tokens)
                }
            }
            else -> {
                if (renderTextContent) {
                    renderTextOrPlaceholder(positionedElement.layoutResult, x, y, width, height, tokens)
                }
            }
        }
    }

    private fun DrawScope.renderTextOrPlaceholder(
        layoutResult: LayoutResult,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens
    ) {
        val layout = (layoutResult as? LayoutResult.TextLayout)?.layout
        if (layout != null) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipRect(x, y, x + width, y + height)
                canvas.nativeCanvas.translate(x, y)
                layout.draw(canvas.nativeCanvas)
                canvas.nativeCanvas.restore()
            }
            return
        }

        drawRect(
            color = Color(tokens.placeholderBorder).copy(alpha = 0.15f),
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )
    }

    private fun DrawScope.renderImage(
        element: DocumentElement.Image,
        layoutResult: LayoutResult,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens
    ) {
        val layout = layoutResult as? LayoutResult.ImageLayout
        val bitmap = File(element.sourceUri)
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }

        if (bitmap != null) {
            val destWidth = unitConverter.ptToPx(layout?.displayWidthPt ?: width)
            val destHeight = unitConverter.ptToPx(layout?.displayHeightPt ?: height)
            val destX = x + (width - destWidth) / 2f
            val destY = y
            val destination = android.graphics.RectF(destX, destY, destX + destWidth, destY + destHeight)

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(bitmap, null, destination, null)
            }

            if (layout?.captionLayout != null) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.clipRect(x, destY + destHeight, x + width, y + height)
                    canvas.nativeCanvas.translate(x, destY + destHeight)
                    layout.captionLayout.draw(canvas.nativeCanvas)
                    canvas.nativeCanvas.restore()
                }
            }
        } else {
            drawRect(
                color = Color(tokens.placeholderBackground),
                topLeft = Offset(x, y),
                size = Size(width, height)
            )
            drawRect(
                color = Color(tokens.placeholderBorder),
                topLeft = Offset(x, y),
                size = Size(width, height),
                style = Stroke(width = 1f)
            )
            drawLine(
                color = Color(tokens.placeholderBorder),
                start = Offset(x, y),
                end = Offset(x + width, y + height),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(tokens.placeholderBorder),
                start = Offset(x + width, y),
                end = Offset(x, y + height),
                strokeWidth = 1f,
                cap = StrokeCap.Round
            )
        }
    }

    private fun DrawScope.renderHeaderFooter(
        element: DocumentElement.HeaderFooter,
        layoutResult: LayoutResult,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens
    ) {
        val separatorColor = Color(tokens.secondaryText).copy(alpha = 0.25f)
        if (element.content.kind == HeaderFooterKind.HEADER) {
            drawLine(separatorColor, Offset(x, y + height), Offset(x + width, y + height), strokeWidth = 0.5f)
        } else {
            drawLine(separatorColor, Offset(x, y), Offset(x + width, y), strokeWidth = 0.5f)
        }
        renderTextOrPlaceholder(layoutResult, x, y, width, height, tokens)
    }

    private fun DrawScope.renderTable(
        element: DocumentElement.Table,
        layoutResult: LayoutResult,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens
    ) {
        val tableLayout = (layoutResult as? LayoutResult.TableLayout)?.layout
        if (tableLayout == null) {
            renderTableGridFallback(element, x, y, width, height, tokens)
            return
        }

        val tableBgColor = Color(tokens.tableBackground)
        val headerBgColor = Color(tokens.tableHeaderBackground)
        val zebraBgColor = Color(tokens.tableZebraBackground)
        val outerBorderColor = Color(tokens.tableBorder)
        val cellBorderColor = Color(tokens.tableCellBorder)

        drawRect(
            color = tableBgColor,
            topLeft = Offset(x, y),
            size = Size(width, height)
        )
        drawRect(
            color = outerBorderColor,
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        var currentY = y
        val cellPaddingPx = unitConverter.ptToPx(tableLayout.cellPaddingPt)

        tableLayout.rowLayouts.forEachIndexed { rowIndex, rowLayout ->
            val rowBottom = (currentY + unitConverter.ptToPx(rowLayout.heightPt)).coerceAtMost(y + height)
            val rowHeightPx = (rowBottom - currentY).coerceAtLeast(0f)
            if (rowHeightPx == 0f) return@forEachIndexed

            when {
                rowLayout.isHeader -> drawRect(
                    color = headerBgColor,
                    topLeft = Offset(x, currentY),
                    size = Size(width, rowHeightPx)
                )
                rowIndex % 2 == 1 -> drawRect(
                    color = zebraBgColor,
                    topLeft = Offset(x, currentY),
                    size = Size(width, rowHeightPx)
                )
            }

            var currentX = x
            rowLayout.cells.forEachIndexed { cellIndex, cell ->
                val remainingWidthPx = (x + width - currentX).coerceAtLeast(0f)
                if (remainingWidthPx == 0f) return@forEachIndexed

                val cellWidthPx = if (cellIndex == rowLayout.cells.lastIndex) {
                    remainingWidthPx
                } else {
                    unitConverter.ptToPx(cell.widthPt).coerceAtMost(remainingWidthPx)
                }

                drawRect(
                    color = cellBorderColor,
                    topLeft = Offset(currentX, currentY),
                    size = Size(cellWidthPx, rowHeightPx),
                    style = Stroke(width = 1f)
                )

                if (cell.layout != null && cell.text.isNotBlank()) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.save()
                        canvas.nativeCanvas.clipRect(
                            currentX + cellPaddingPx,
                            currentY + cellPaddingPx,
                            currentX + cellWidthPx - cellPaddingPx,
                            currentY + rowHeightPx - cellPaddingPx
                        )
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
        height: Float,
        tokens: DocumentColorTokens
    ) {
        val rowCount = element.rows.size.coerceAtLeast(1)
        val columnCount = element.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val rowHeight = height / rowCount
        val columnWidth = width / columnCount

        val tableBgColor = Color(tokens.tableBackground)
        val headerBgColor = Color(tokens.tableHeaderBackground)
        val outerBorderColor = Color(tokens.tableBorder)
        val gridLineColor = Color(tokens.tableCellBorder)

        drawRect(
            color = tableBgColor,
            topLeft = Offset(x, y),
            size = Size(width, height)
        )
        if (element.hasHeader && rowCount > 0) {
            drawRect(
                color = headerBgColor,
                topLeft = Offset(x, y),
                size = Size(width, rowHeight)
            )
        }
        drawRect(
            color = outerBorderColor,
            topLeft = Offset(x, y),
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        for (rowIndex in 1 until rowCount) {
            val lineY = y + (rowHeight * rowIndex)
            drawLine(
                color = gridLineColor,
                start = Offset(x, lineY),
                end = Offset(x + width, lineY),
                strokeWidth = 1f
            )
        }

        for (columnIndex in 1 until columnCount) {
            val lineX = x + (columnWidth * columnIndex)
            drawLine(
                color = gridLineColor,
                start = Offset(lineX, y),
                end = Offset(lineX, y + height),
                strokeWidth = 1f
            )
        }
    }
}
