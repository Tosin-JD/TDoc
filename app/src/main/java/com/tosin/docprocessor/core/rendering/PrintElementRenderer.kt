package com.tosin.docprocessor.core.rendering

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.LayoutResult
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.model.layout.TableCellLayout
import com.tosin.docprocessor.domain.model.layout.TableRenderLayout
import com.tosin.docprocessor.domain.pagination.UnitConverter
import java.io.File

class PrintElementRenderer(
    private val unitConverter: UnitConverter
) {
    fun DrawScope.render(
        positionedElement: PositionedElement,
        renderTextContent: Boolean = true,
        activeEditingElementId: String? = null,
        isDarkMode: Boolean = false
    ) {
        val bounds = positionedElement.bounds
        val x = unitConverter.ptToPx(bounds.left)
        val y = unitConverter.ptToPx(bounds.top)
        val width = unitConverter.ptToPx(bounds.width())
        val height = unitConverter.ptToPx(bounds.height())
        val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light

        val isBeingEdited = activeEditingElementId != null && (
            positionedElement.elementId == activeEditingElementId ||
            positionedElement.logicalElementId == activeEditingElementId
        )
        val shouldRenderText = renderTextContent && !isBeingEdited

        when (val element = positionedElement.element) {
            is DocumentElement.Image ->
                renderImage(element, positionedElement.layoutResult, x, y, width, height, tokens)
            is DocumentElement.Table ->
                renderTable(element, positionedElement.layoutResult, x, y, width, height, tokens, isDarkMode)
            is DocumentElement.Drawing ->
                renderDrawing(element, positionedElement.layoutResult, x, y, width, height, tokens, isDarkMode)
            is DocumentElement.HeaderFooter -> {
                if (shouldRenderText) {
                    renderHeaderFooter(element, positionedElement.layoutResult, x, y, width, height, tokens)
                }
            }
            else -> {
                if (shouldRenderText) {
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

    /**
     * Paints a drawing's authored fill/stroke (when parsed) behind its
     * placeholder text so shape colours survive into print view.
     */
    private fun DrawScope.renderDrawing(
        element: DocumentElement.Drawing,
        layoutResult: LayoutResult,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean
    ) {
        val pageBg = tokens.pageBackground
        DocumentTextColorResolver.resolveBackgroundColor(
            element.info.fillColor, tokens, isDarkMode, pageBg
        )?.let { argb ->
            drawRect(
                color = Color(argb),
                topLeft = Offset(x, y),
                size = Size(width, height)
            )
        }
        DocumentTextColorResolver.parseColorString(element.info.strokeColor)?.let { argb ->
            val stroke = DocumentTextColorResolver.resolveBorderColorArgb(
                argb, tokens, isDarkMode, pageBg
            )
            drawRect(
                color = Color(stroke),
                topLeft = Offset(x, y),
                size = Size(width, height),
                style = Stroke(width = 1f)
            )
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
        tokens: DocumentColorTokens,
        isDarkMode: Boolean
    ) {
        val tableLayout = (layoutResult as? LayoutResult.TableLayout)?.layout
        if (tableLayout == null || tableLayout.rowLayouts.isEmpty()) {
            renderTableGridFallback(element, x, y, width, height, tokens, isDarkMode)
            return
        }

        // Table-level document shading resolved for the current theme; absent
        // shading stays transparent.
        val pageBg = tokens.pageBackground
        val tableShadingSource = element.presentation?.shading?.fill
            ?: element.metadata.shadingColor
        val tableFill = DocumentTextColorResolver.resolveBackgroundColor(
            tableShadingSource, tokens, isDarkMode, pageBg
        )
        tableFill?.let { argb ->
            drawRect(
                color = Color(argb),
                topLeft = Offset(x, y),
                size = Size(width, height)
            )
        }
        val underTable = tableFill ?: pageBg

        val autoColor = Color(DocumentTextColorResolver.resolvePlainTextColor(tokens, isDarkMode))

        val columnWidthsPx = resolveColumnWidthsPx(tableLayout, width)
        val columnLefts = FloatArray(columnWidthsPx.size + 1)
        columnLefts[0] = x
        for (column in columnWidthsPx.indices) {
            columnLefts[column + 1] = columnLefts[column] + columnWidthsPx[column]
        }

        val rowTops = FloatArray(tableLayout.rowLayouts.size + 1)
        rowTops[0] = y
        for (rowIndex in tableLayout.rowLayouts.indices) {
            val next = rowTops[rowIndex] + unitConverter.ptToPx(tableLayout.rowLayouts[rowIndex].heightPt)
            rowTops[rowIndex + 1] = next.coerceAtMost(y + height)
        }

        // Resolve every visible cell's frame once, then paint in three passes —
        // backgrounds, borders, text — so a later cell's background can never
        // overdraw a neighbour's border stroke (Word paints borders on top of
        // shading). Covered (merge continuation) cells are skipped entirely;
        // the anchor draws its full region.
        val frames = buildList {
            tableLayout.rowLayouts.forEachIndexed { rowIndex, rowLayout ->
                rowLayout.cells.forEachIndexed { columnIndex, cell ->
                    if (cell.isCovered) return@forEachIndexed
                    if (columnIndex >= columnWidthsPx.size) return@forEachIndexed

                    val colSpan = cell.colSpan.coerceIn(1, columnWidthsPx.size - columnIndex)
                    val rowSpan = cell.rowSpan.coerceIn(1, tableLayout.rowLayouts.size - rowIndex)
                    val left = columnLefts[columnIndex]
                    val right = columnLefts[columnIndex + colSpan]
                    val top = rowTops[rowIndex]
                    val bottom = rowTops[(rowIndex + rowSpan).coerceAtMost(tableLayout.rowLayouts.size)]
                    val cellWidthPx = (right - left).coerceAtLeast(0f)
                    val cellHeightPx = (bottom - top).coerceAtLeast(0f)
                    if (cellWidthPx <= 0f || cellHeightPx <= 0f) return@forEachIndexed
                    add(CellFrame(cell, left, top, right, bottom))
                }
            }
        }

        // Pass 1: cell backgrounds over the table shading. Colours are
        // resolved for the current theme against the table/page background so
        // fills authored for light pages stay readable on dark pages.
        frames.forEach { frame ->
            frame.cell.backgroundColor?.let { hex ->
                DocumentTextColorResolver.resolveBackgroundColor(
                    hex, tokens, isDarkMode, underTable
                )?.let { argb ->
                    drawRect(
                        color = Color(argb),
                        topLeft = Offset(frame.left, frame.top),
                        size = Size(frame.right - frame.left, frame.bottom - frame.top)
                    )
                }
            }
        }

        // Pass 2: borders on top of every background.
        frames.forEach { frame ->
            val cellFill = DocumentTextColorResolver.resolveBackgroundColor(
                frame.cell.backgroundColor, tokens, isDarkMode, underTable
            ) ?: underTable
            drawCellBorders(
                cell = frame.cell,
                table = element,
                left = frame.left,
                top = frame.top,
                right = frame.right,
                bottom = frame.bottom,
                fallbackColor = autoColor,
                tokens = tokens,
                isDarkMode = isDarkMode,
                backgroundArgb = cellFill
            )
        }

        // Pass 3: rich text, clipped to each cell's padding box.
        frames.forEach { frame ->
            val cell = frame.cell
            val layout = cell.layout ?: return@forEach
            if (cell.text.isBlank()) return@forEach

            val padding = resolveCellPaddingPx(cell, tableLayout)
            val innerLeft = frame.left + padding.left
            val innerTop = frame.top + padding.top
            val innerWidth = ((frame.right - frame.left) - padding.left - padding.right).coerceAtLeast(0f)
            val innerHeight = ((frame.bottom - frame.top) - padding.top - padding.bottom).coerceAtLeast(0f)
            val contentHeightPx = unitConverter.ptToPx(cell.contentHeightPt)
            val verticalOffset = when (cell.verticalAlignment) {
                VerticalCellAlign.CENTER ->
                    ((innerHeight - contentHeightPx) / 2f).coerceAtLeast(0f)
                VerticalCellAlign.BOTTOM ->
                    (innerHeight - contentHeightPx).coerceAtLeast(0f)
                VerticalCellAlign.TOP -> 0f
            }

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipRect(
                    innerLeft,
                    innerTop,
                    innerLeft + innerWidth,
                    innerTop + innerHeight
                )
                canvas.nativeCanvas.translate(innerLeft, innerTop + verticalOffset)
                layout.draw(canvas.nativeCanvas)
                canvas.nativeCanvas.restore()
            }
        }
    }

    private data class CellFrame(
        val cell: TableCellLayout,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private data class CellPaddingPx(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private data class ResolvedBorder(
        val widthPx: Float,
        val color: Color,
        val style: String?
    )

    private fun resolveColumnWidthsPx(tableLayout: TableRenderLayout, width: Float): List<Float> {
        val grid = tableLayout.gridColumnWidthsPt
        val widths = if (grid.size == tableLayout.columnCount && grid.sum() > 0f) {
            grid.map { unitConverter.ptToPx(it).coerceAtLeast(0.5f) }
        } else {
            val maxColumns = tableLayout.rowLayouts.maxOfOrNull { it.cells.size } ?: 0
            List(maxColumns) { column ->
                tableLayout.rowLayouts
                    .mapNotNull { it.cells.getOrNull(column) }
                    .filter { !it.isCovered }
                    .maxOfOrNull { unitConverter.ptToPx(it.widthPt) }
                    ?: unitConverter.ptToPx(tableLayout.cellPaddingPt * 4f)
            }
        }
        if (widths.isEmpty()) return listOf(width)

        val total = widths.sum()
        val scaled = if (total > 0f) {
            widths.map { it * (width / total) }
        } else {
            List(widths.size) { width / widths.size }
        }.toMutableList()
        // Absorb rounding drift in the last column so borders align with bounds.
        scaled[scaled.lastIndex] += width - scaled.sum()
        return scaled.map { it.coerceAtLeast(0f) }
    }

    private fun resolveCellPaddingPx(cell: TableCellLayout, tableLayout: TableRenderLayout): CellPaddingPx {
        val fallback = unitConverter.ptToPx(tableLayout.cellPaddingPt)
        fun side(points: Double?): Float =
            points?.takeIf { it >= 0.0 }?.toFloat()?.let(unitConverter::ptToPx) ?: fallback
        val margins = cell.margins
        return CellPaddingPx(
            left = side(margins?.left?.widthPoints),
            top = side(margins?.top?.widthPoints),
            right = side(margins?.right?.widthPoints),
            bottom = side(margins?.bottom?.widthPoints)
        )
    }

    private fun DrawScope.drawCellBorders(
        cell: TableCellLayout,
        table: DocumentElement.Table,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        fallbackColor: Color,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        backgroundArgb: Int
    ) {
        // Resolved presentations carry shared-edge ownership in paintBorders:
        // only the edges this cell owns are painted and every edge was already
        // resolved against its neighbour, so no table-level fallback applies.
        val paintBorders = cell.paintBorders
        if (paintBorders != null) {
            drawBorderSide(paintBorders.top, null, left, top, right, top, fallbackColor, tokens, isDarkMode, backgroundArgb)
            drawBorderSide(paintBorders.bottom, null, left, bottom, right, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
            drawBorderSide(paintBorders.left, null, left, top, left, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
            drawBorderSide(paintBorders.right, null, right, top, right, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
            return
        }
        val tableBorders = table.metadata.borders
        val cellBorders = cell.borders
        drawBorderSide(cellBorders?.top, tableBorders.top, left, top, right, top, fallbackColor, tokens, isDarkMode, backgroundArgb)
        drawBorderSide(cellBorders?.bottom, tableBorders.bottom, left, bottom, right, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
        drawBorderSide(cellBorders?.left, tableBorders.left, left, top, left, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
        drawBorderSide(cellBorders?.right, tableBorders.right, right, top, right, bottom, fallbackColor, tokens, isDarkMode, backgroundArgb)
    }

    private fun DrawScope.drawBorderSide(
        cellSide: Border?,
        tableSide: Border?,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        fallbackColor: Color,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        backgroundArgb: Int
    ) {
        val border = resolveBorder(cellSide, tableSide, fallbackColor, tokens, isDarkMode, backgroundArgb) ?: return
        val pathEffect = when (border.style?.lowercase()) {
            "dashed", "dashsmallgap", "dashdotstroke", "dotdash" ->
                PathEffect.dashPathEffect(floatArrayOf(border.widthPx * 4f, border.widthPx * 3f))
            "dotted", "dot" ->
                PathEffect.dashPathEffect(floatArrayOf(border.widthPx, border.widthPx * 2f))
            else -> null
        }

        if (border.style?.lowercase() == "double") {
            val inset = border.widthPx * 1.5f
            val dx = if (startX == endX) inset else 0f
            val dy = if (startY == endY) inset else 0f
            val dx2 = if (startX != endX) -inset else 0f
            val dy2 = if (startY != endY) -inset else 0f
            drawLine(border.color, Offset(startX + dx, startY + dy), Offset(endX + dx, endY + dy), border.widthPx)
            drawLine(border.color, Offset(startX + dx2, startY + dy2), Offset(endX + dx2, endY + dy2), border.widthPx)
        } else {
            drawLine(
                color = border.color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = border.widthPx,
                pathEffect = pathEffect
            )
        }
    }

    private fun resolveBorder(
        cellSide: Border?,
        tableSide: Border?,
        fallbackColor: Color,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean,
        backgroundArgb: Int
    ): ResolvedBorder? {
        // Unspecified on both levels: paint nothing. Only an explicit border
        // (cell or table) produces a line; there is no theme grid fallback.
        val source = cellSide ?: tableSide ?: return null
        val style = source.style?.lowercase()
        if (style == "nil" || style == "none" || style == "zero") return null
        if (source.widthPoints != null && source.widthPoints <= 0.0) return null

        val widthPt = source.widthPoints?.toFloat() ?: DEFAULT_BORDER_WIDTH_PT
        val parsed = DocumentTextColorResolver.parseColorString(source.color)
        val color = if (parsed == null || ((parsed ushr 24) and 0xFF) == 0) {
            Color(DocumentTextColorResolver.resolveBorderColor(source.color, tokens, isDarkMode, backgroundArgb))
        } else {
            Color(DocumentTextColorResolver.resolveBorderColorArgb(parsed, tokens, isDarkMode, backgroundArgb))
        }
        return ResolvedBorder(
            widthPx = kotlin.math.max(unitConverter.ptToPx(widthPt.coerceAtLeast(0.5f)), 1f),
            color = color,
            style = style
        )
    }

    /**
     * Structural fallback for tables without a measurable layout: paints only
     * document-authored table shading. No theme fills, borders or grid lines —
     * this path exists so malformed tables degrade transparently instead of
     * claiming a styling the document never specified.
     */
    private fun DrawScope.renderTableGridFallback(
        element: DocumentElement.Table,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tokens: DocumentColorTokens,
        isDarkMode: Boolean
    ) {
        val tableShading = element.presentation?.shading?.fill
            ?: element.metadata.shadingColor
        DocumentTextColorResolver.resolveBackgroundColor(
            tableShading, tokens, isDarkMode, tokens.pageBackground
        )?.let { argb ->
            drawRect(
                color = Color(argb),
                topLeft = Offset(x, y),
                size = Size(width, height)
            )
        }
    }

    private companion object {
        const val DEFAULT_BORDER_WIDTH_PT = 0.5f
    }
}
