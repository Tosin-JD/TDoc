package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.core.color.DocumentTextColorResolver
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TableBorderSide
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.components.table.TableColumnSizer
import com.tosin.docprocessor.ui.components.table.TableLayoutConfig
import com.tosin.docprocessor.ui.components.table.TableUiMapper
import com.tosin.docprocessor.ui.components.table.model.CellTextAlignment
import com.tosin.docprocessor.ui.components.table.model.TableCellUi
import com.tosin.docprocessor.ui.components.table.model.TableUiModel
import com.tosin.docprocessor.ui.editor.renderer.AnnotatedStringMappers
import com.tosin.docprocessor.ui.theme.LocalAppIsDarkTheme
import kotlin.math.max

/**
 * Convenience overload for callers that still hold a [DocumentElement.Table].
 * The mapping/normalization is delegated to [TableUiMapper].
 */
@Composable
fun TableWidget(table: DocumentElement.Table) {
    val mapper = remember { TableUiMapper() }
    val model = remember(table) { mapper.map(table) }
    DocumentTable(model = model)
}

/**
 * Responsive document table that paints only document-authored formatting.
 *
 * - Fills the available width when it fits; becomes horizontally scrollable
 *   when columns overflow (small screens).
 * - No rounded clipping, theme fill, synthetic borders, zebra rows or header
 *   typography: an unstyled document table renders transparent, square and
 *   borderless.
 * - Honours merged cells (`colSpan`/`rowSpan`), resolved cell/row/table
 *   shading, per-side borders, cell margins and rich styled spans.
 *
 * The composable is a dumb view over an immutable [TableUiModel]; all layout
 * math lives in [TableColumnSizer] and the custom [Layout] below.
 */
@Composable
fun DocumentTable(
    model: TableUiModel,
    modifier: Modifier = Modifier,
    config: TableLayoutConfig = TableLayoutConfig.Default
) {
    if (model.isEmpty) return

    val isDarkMode = LocalAppIsDarkTheme.current
    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Bootstrap `.table-responsive` semantics: keep natural column widths
        // inside a horizontally scrollable wrapper. The readability floor is
        // viewport-aware so phones get narrower minimum columns (a layout
        // affordance, not document styling).
        val effectiveConfig = remember(config, maxWidth) {
            val minWidthDp = when {
                maxWidth < 360.dp -> minOf(config.minColumnWidthDp, 88f)
                maxWidth < 600.dp -> minOf(config.minColumnWidthDp, 104f)
                else -> config.minColumnWidthDp
            }
            if (minWidthDp == config.minColumnWidthDp) config
            else config.copy(minColumnWidthDp = minWidthDp)
        }
        val layout = remember(model, maxWidth, effectiveConfig) {
            TableColumnSizer.compute(
                columnWeights = model.columnWeights,
                availableWidthDp = maxWidth.value,
                config = effectiveConfig,
                preferredWidthsDp = model.sourceWidthsPt
                    .takeIf { it.size == model.columnCount }
                    ?.map { pointsToDp(it.toDouble()) }
            )
        }
        // A document-authored width narrower than the viewport sizes the table
        // to that width (aligned per `w:jc`/ODF align) instead of stretching.
        val authoredWidthDp = model.preferredWidthPt
            ?.let { pointsToDp(it.toDouble()) }
            ?.takeIf { it in 1f..maxWidth.value }
        val tableWidth = when {
            layout.requiresScroll -> layout.totalWidthDp.dp
            authoredWidthDp != null -> authoredWidthDp.dp
            else -> maxWidth
        }
        val tableAlignment = when (model.tableAlignment) {
            CellTextAlignment.CENTER -> Alignment.TopCenter
            CellTextAlignment.END -> Alignment.TopEnd
            else -> Alignment.TopStart
        }

        Box(
            modifier = Modifier
                .align(tableAlignment)
                .horizontalScroll(scrollState)
                .testTag("table-scroll")
        ) {
            Box(
                modifier = Modifier
                    .width(tableWidth)
                    // Table-level document shading only; absent shading stays transparent.
                    .background(
                        DocumentTextColorResolver.resolveBackgroundColor(
                            model.tableBackgroundSource, tokens, isDarkMode
                        )?.let { Color(it) } ?: Color.Transparent
                    )
            ) {
                TableGrid(
                    model = model,
                    columnWidthsDp = layout.columnWidthsDp,
                    config = config,
                    tokens = tokens,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

/** Points (1/72in) to dp (1/160in): dp = pt * 160 / 72. */
private fun pointsToDp(points: Double): Float = (points * 160.0 / 72.0).toFloat()

private data class CellPlacement(
    val rowIndex: Int,
    val columnIndex: Int,
    val cell: TableCellUi
)

@Composable
private fun TableGrid(
    model: TableUiModel,
    columnWidthsDp: List<Float>,
    config: TableLayoutConfig,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean
) {
    val placements = remember(model, columnWidthsDp.size) {
        buildList {
            model.rows.forEachIndexed { rowIndex, row ->
                row.cells.forEachIndexed { columnIndex, cell ->
                    if (cell.isCovered || columnIndex >= model.columnCount) return@forEachIndexed
                    add(CellPlacement(rowIndex, columnIndex, cell))
                }
            }
        }
    }

    Layout(
        content = {
            placements.forEach { placement ->
                // Three layers per cell — background, borders, text — so a
                // later cell's background can never overdraw a neighbour's
                // border stroke (borders paint on top of shading).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawCellBackground(placement.cell, model, tokens, isDarkMode)
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawCellBorders(placement, model, tokens, isDarkMode)
                        }
                )
                CellText(placement = placement, tokens = tokens, isDarkMode = isDarkMode)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val columnCount = model.columnCount.coerceAtLeast(1)
        val columnWidthsPx = FloatArray(columnCount) { index ->
            columnWidthsDp.getOrElse(index) { config.minColumnWidthDp }.dp.toPx()
        }
        val reportedWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        if (reportedWidth > 0) {
            val drift = reportedWidth - columnWidthsPx.sum()
            val last = columnWidthsPx.lastIndex
            columnWidthsPx[last] = (columnWidthsPx[last] + drift).coerceAtLeast(1f)
        }
        val columnOffsets = FloatArray(columnCount + 1)
        for (column in 0 until columnCount) {
            columnOffsets[column + 1] = columnOffsets[column] + columnWidthsPx[column]
        }
        val tableWidthPx = columnOffsets.last().toInt().coerceAtLeast(1)
        val rowCount = model.rows.size.coerceAtLeast(1)
        val minRowHeightPx = DEFAULT_CELL_PADDING_PX * 2f

        // Measure text first (fixed width, free height) to derive row heights.
        val contentPlaceables = placements.mapIndexed { index, placement ->
            val span = placement.cell.colSpan.coerceIn(1, columnCount - placement.columnIndex)
            val cellWidthPx = (columnOffsets[placement.columnIndex + span] -
                columnOffsets[placement.columnIndex]).toInt().coerceAtLeast(1)
            measurables[index * 3 + 2].measure(Constraints.fixedWidth(cellWidthPx))
        }

        val rowHeights = FloatArray(rowCount) { minRowHeightPx }
        placements.forEachIndexed { index, placement ->
            val span = placement.cell.rowSpan.coerceIn(1, rowCount - placement.rowIndex)
            if (span == 1) {
                rowHeights[placement.rowIndex] = max(
                    rowHeights[placement.rowIndex],
                    contentPlaceables[index].height.toFloat()
                )
            }
        }
        placements
            .withIndex()
            .filter { (_, placement) -> placement.cell.rowSpan > 1 }
            .sortedBy { (_, placement) -> placement.cell.rowSpan }
            .forEach { (index, placement) ->
                val span = placement.cell.rowSpan.coerceIn(1, rowCount - placement.rowIndex)
                val endRow = (placement.rowIndex + span).coerceAtMost(rowCount)
                if (endRow <= placement.rowIndex) return@forEach
                val required = contentPlaceables[index].height.toFloat()
                val current = (placement.rowIndex until endRow).sumOf { rowHeights[it].toDouble() }.toFloat()
                if (required > current) {
                    val extra = (required - current) / (endRow - placement.rowIndex)
                    for (row in placement.rowIndex until endRow) rowHeights[row] += extra
                }
            }

        val rowOffsets = FloatArray(rowCount + 1)
        for (row in 0 until rowCount) {
            rowOffsets[row + 1] = rowOffsets[row] + rowHeights[row].coerceAtLeast(minRowHeightPx)
        }
        val tableHeightPx = rowOffsets.last().toInt().coerceAtLeast(1)

        // Measure the background and border layers for every placement.
        val backgroundPlaceables = placements.mapIndexed { index, placement ->
            val colSpan = placement.cell.colSpan.coerceIn(1, columnCount - placement.columnIndex)
            val rowSpan = placement.cell.rowSpan.coerceIn(1, rowCount - placement.rowIndex)
            val widthPx = (columnOffsets[placement.columnIndex + colSpan] -
                columnOffsets[placement.columnIndex]).toInt().coerceAtLeast(1)
            val heightPx = (rowOffsets[placement.rowIndex + rowSpan] -
                rowOffsets[placement.rowIndex]).toInt().coerceAtLeast(1)
            measurables[index * 3].measure(Constraints.fixed(widthPx, heightPx)) to
                measurables[index * 3 + 1].measure(Constraints.fixed(widthPx, heightPx))
        }

        layout(tableWidthPx, tableHeightPx) {
            placements.forEachIndexed { index, placement ->
                val colSpan = placement.cell.colSpan.coerceIn(1, columnCount - placement.columnIndex)
                val rowSpan = placement.cell.rowSpan.coerceIn(1, rowCount - placement.rowIndex)
                val x = columnOffsets[placement.columnIndex].toInt()
                val y = rowOffsets[placement.rowIndex].toInt()
                val heightPx = (rowOffsets[placement.rowIndex + rowSpan] -
                    rowOffsets[placement.rowIndex]).toInt()

                backgroundPlaceables[index].first.place(x, y)
                backgroundPlaceables[index].second.place(x, y)

                val content = contentPlaceables[index]
                val verticalOffset = when (placement.cell.verticalAlignment) {
                    VerticalCellAlign.CENTER -> ((heightPx - content.height) / 2).coerceAtLeast(0)
                    VerticalCellAlign.BOTTOM -> (heightPx - content.height).coerceAtLeast(0)
                    VerticalCellAlign.TOP -> 0
                }
                content.place(x, y + verticalOffset)
            }
        }
    }
}

@Composable
private fun CellText(
    placement: CellPlacement,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean
) {
    val cell = placement.cell
    // Effective local background for contrast: cell fill → table fill → page.
    val cellBackgroundArgb = cell.backgroundColorSource
        ?.let { DocumentTextColorResolver.resolveBackgroundColor(it, tokens, isDarkMode) }
        ?: tokens.pageBackground
    val annotated: AnnotatedString? = remember(cell.spans, tokens, isDarkMode, cellBackgroundArgb) {
        if (cell.spans.isEmpty()) null
        else AnnotatedStringMappers.spansToAnnotatedString(
            cell.spans, tokens, isDarkMode, cellBackgroundArgb
        )
    }
    val fallbackColor = Color(
        DocumentTextColorResolver.resolveTextColor(null, tokens, isDarkMode, cellBackgroundArgb)
    )

    Box(
        modifier = Modifier
            .padding(
                start = cell.paddingFor(TableBorderSide.LEFT).dp,
                top = cell.paddingFor(TableBorderSide.TOP).dp,
                end = cell.paddingFor(TableBorderSide.RIGHT).dp,
                bottom = cell.paddingFor(TableBorderSide.BOTTOM).dp
            )
            .fillMaxWidth(),
        contentAlignment = cell.verticalAlignment.toAlignment()
    ) {
        when {
            annotated != null && annotated.isNotEmpty() -> Text(
                text = annotated,
                modifier = Modifier.fillMaxWidth(),
                color = fallbackColor,
                textAlign = cell.alignment.toTextAlign()
            )

            cell.text.isNotEmpty() -> Text(
                text = cell.text,
                modifier = Modifier.fillMaxWidth(),
                color = fallbackColor,
                textAlign = cell.alignment.toTextAlign()
            )
        }
    }
}

/** Document cell padding in dp: resolved margins in points, else renderer default. */
private fun TableCellUi.paddingFor(side: TableBorderSide): Float =
    margins?.side(side)?.widthPoints?.toFloat()?.takeIf { it >= 0f }
        ?.let { pointsToDp(it.toDouble()) }
        ?: DEFAULT_CELL_PADDING_DP

private data class UiBorder(
    val widthPx: Float,
    val color: Color,
    val style: String?
)

private fun DrawScope.drawCellBackground(
    cell: TableCellUi,
    model: TableUiModel,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean
) {
    // Cell shading only — table/row/region shading is already resolved into
    // this value by the presentation mapper; nothing is added synthetically.
    DocumentTextColorResolver.resolveBackgroundColor(
        cell.backgroundColorSource, tokens, isDarkMode
    )?.let { drawRect(color = Color(it)) }
}

private fun DrawScope.drawCellBorders(
    placement: CellPlacement,
    model: TableUiModel,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean
) {
    val cell = placement.cell
    // Borders are contrast-resolved against the effective local background
    // (cell fill → table fill → page), not the page alone.
    val underCell = cell.backgroundColorSource
        ?.let { DocumentTextColorResolver.resolveBackgroundColor(it, tokens, isDarkMode) }
        ?: model.tableBackgroundSource
            ?.let { DocumentTextColorResolver.resolveBackgroundColor(it, tokens, isDarkMode) }
        ?: tokens.pageBackground
    val autoColor = Color(
        DocumentTextColorResolver.resolveBorderColor(null, tokens, isDarkMode, underCell)
    )

    val borders = if (cell.paintBordersResolved) {
        cell.borders ?: CellBorders()
    } else {
        // Legacy path: raw cell borders over the table-level fallback.
        val tableBorders = model.outerBorders ?: CellBorders()
        CellBorders(
            top = cell.borders?.top ?: tableBorders.top,
            right = cell.borders?.right ?: tableBorders.right,
            bottom = cell.borders?.bottom ?: tableBorders.bottom,
            left = cell.borders?.left ?: tableBorders.left
        )
    }

    drawBorderSide(borders.top, horizontal = true, atStart = true, autoColor = autoColor, tokens = tokens, isDarkMode = isDarkMode, backgroundArgb = underCell)
    drawBorderSide(borders.bottom, horizontal = true, atStart = false, autoColor = autoColor, tokens = tokens, isDarkMode = isDarkMode, backgroundArgb = underCell)
    drawBorderSide(borders.left, horizontal = false, atStart = true, autoColor = autoColor, tokens = tokens, isDarkMode = isDarkMode, backgroundArgb = underCell)
    drawBorderSide(borders.right, horizontal = false, atStart = false, autoColor = autoColor, tokens = tokens, isDarkMode = isDarkMode, backgroundArgb = underCell)
}

private fun DrawScope.drawBorderSide(
    border: Border?,
    horizontal: Boolean,
    atStart: Boolean,
    autoColor: Color,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean,
    backgroundArgb: Int
) {
    val resolved = resolveUiBorder(border, autoColor, tokens, isDarkMode, backgroundArgb) ?: return
    val pathEffect = when (resolved.style?.lowercase()) {
        "dashed", "dashsmallgap", "dashdotstroke", "dotdash" ->
            PathEffect.dashPathEffect(floatArrayOf(resolved.widthPx * 4f, resolved.widthPx * 3f))
        "dotted", "dot" ->
            PathEffect.dashPathEffect(floatArrayOf(resolved.widthPx, resolved.widthPx * 2f))
        else -> null
    }
    drawEdge(resolved, horizontal, atStart, pathEffect)
}

private fun DrawScope.drawEdge(
    border: UiBorder,
    horizontal: Boolean,
    atStart: Boolean,
    pathEffect: PathEffect?
) {
    val center = border.widthPx / 2f
    val start: Offset
    val end: Offset
    if (horizontal) {
        val y = if (atStart) center else size.height - center
        start = Offset(0f, y)
        end = Offset(size.width, y)
    } else {
        val x = if (atStart) center else size.width - center
        start = Offset(x, 0f)
        end = Offset(x, size.height)
    }
    if (border.style?.lowercase() == "double") {
        val gap = border.widthPx * 1.5f
        val dx = if (horizontal) 0f else gap
        val dy = if (horizontal) gap else 0f
        drawLine(border.color, start, end, border.widthPx)
        drawLine(
            border.color,
            Offset(start.x + dx, start.y + dy),
            Offset(end.x + dx, end.y + dy),
            border.widthPx
        )
    } else {
        drawLine(
            color = border.color,
            start = start,
            end = end,
            strokeWidth = border.widthPx,
            pathEffect = pathEffect
        )
    }
}

/**
 * Resolves one border side to paint instructions. Returns null when the side
 * is unspecified or explicitly none — absent borders never become theme
 * grid lines.
 */
private fun resolveUiBorder(
    border: Border?,
    autoColor: Color,
    tokens: DocumentColorTokens,
    isDarkMode: Boolean,
    backgroundArgb: Int
): UiBorder? {
    if (border == null) return null
    val style = border.style?.lowercase()
    if (style == "nil" || style == "none" || style == "zero") return null
    if (border.widthPoints != null && border.widthPoints <= 0.0) return null
    val widthPx = border.widthPoints?.toFloat()
        ?.let { max(it, 0.5f) * PT_TO_PX }
        ?: DEFAULT_BORDER_WIDTH_PX
    val parsed = DocumentTextColorResolver.parseColorString(border.color)
    val color = if (parsed == null || ((parsed ushr 24) and 0xFF) == 0) {
        Color(DocumentTextColorResolver.resolveBorderColor(border.color, tokens, isDarkMode, backgroundArgb))
    } else {
        Color(DocumentTextColorResolver.resolveBorderColorArgb(parsed, tokens, isDarkMode, backgroundArgb))
    }
    return UiBorder(
        widthPx = max(widthPx, 1f),
        color = color,
        style = style
    )
}

private fun VerticalCellAlign.toAlignment(): Alignment = when (this) {
    VerticalCellAlign.TOP -> Alignment.TopStart
    VerticalCellAlign.CENTER -> Alignment.CenterStart
    VerticalCellAlign.BOTTOM -> Alignment.BottomStart
}

private fun CellTextAlignment.toTextAlign(): TextAlign = when (this) {
    CellTextAlignment.START -> TextAlign.Start
    CellTextAlignment.CENTER -> TextAlign.Center
    CellTextAlignment.END -> TextAlign.End
}

private const val PT_TO_PX = 96f / 72f
private const val DEFAULT_BORDER_WIDTH_PX = 1f
private const val DEFAULT_CELL_PADDING_DP = 8f
private const val DEFAULT_CELL_PADDING_PX = 16f
