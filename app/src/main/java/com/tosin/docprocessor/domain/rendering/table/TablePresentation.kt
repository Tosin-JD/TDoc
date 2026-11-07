package com.tosin.docprocessor.domain.rendering.table

import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableBorderSide
import com.tosin.docprocessor.domain.document.model.style.TableRegion
import com.tosin.docprocessor.domain.document.model.style.TableRegionStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge

/**
 * Renderer-ready, fully resolved table presentation.
 *
 * This is the single style-resolution contract shared by the mobile Compose
 * renderer and the print paginator/renderer. Every paintable property has
 * already gone through the precedence chain
 * `cell direct` > `conditional table-style region` > `table direct/style` >
 * `none`, so consumers never invent document styling and never substitute
 * application theme tokens.
 *
 * All measurements are in points; colours are the authored hex strings.
 */
data class TablePresentation(
    val columnCount: Int,
    /** Table width in points (`w:tblW`/`style:width`); null = auto. */
    val widthPoints: Double?,
    val alignment: TextAlignment?,
    /** Table-level shading painted behind the whole grid. Null = transparent. */
    val shading: Shading?,
    /** Resolved outer table borders (explicit `nil`/`none` preserved). */
    val borders: CellBorders,
    /** Resolved interior borders (`insideH`/`insideV`); null = none specified. */
    val insideBorders: CellBorders?,
    /** Resolved table default cell margins in points. */
    val cellMargins: CellBorders?,
    /** Table layout hint (`w:tblLayout` type: `fixed`/`autofit`); null = auto. */
    val layout: String? = null,
    /** Source grid column widths in points; may contain nulls. */
    val gridColumnWidthsPt: List<Double?>,
    val caption: String?,
    val description: String?,
    val rows: List<TableRowPresentation>
) {
    val isEmpty: Boolean get() = rows.isEmpty() || columnCount == 0
}

/**
 * Slices a resolved presentation for a page break after [rowsThatFit] rows.
 * Mirrors how document rows are split: the continuation fragment repeats the
 * first (header) row when [repeatHeader] is set so its resolved borders,
 * shading and margins stay aligned with the repeated header.
 */
fun TablePresentation.splitForContinuation(
    rowsThatFit: Int,
    repeatHeader: Boolean
): Pair<TablePresentation, TablePresentation> {
    fun split(values: List<TableRowPresentation>): Pair<List<TableRowPresentation>, List<TableRowPresentation>> {
        if (values.isEmpty()) return emptyList<TableRowPresentation>() to emptyList()
        val first = values.take(rowsThatFit)
        val remaining = buildList {
            if (repeatHeader) add(values.first())
            addAll(values.drop(rowsThatFit))
        }
        return first to remaining
    }
    val (firstRows, remainingRows) = split(rows)
    return copy(rows = firstRows) to copy(rows = remainingRows)
}

data class TableRowPresentation(
    val isHeader: Boolean,
    val heightPoints: Double?,
    /** Dense grid: exactly [TablePresentation.columnCount] entries per row. */
    val cells: List<TableCellPresentation>
)

data class TableCellPresentation(
    val columnIndex: Int,
    val colSpan: Int,
    val rowSpan: Int,
    /** True for hMerge/vMerge continuation positions — renderers must skip. */
    val isCovered: Boolean,
    val widthPoints: Double?,
    /**
     * Resolved cell shading (cell direct > conditional region). Null fill or
     * null shading paints nothing; the table-level [TablePresentation.shading]
     * remains visible underneath.
     */
    val shading: Shading?,
    /**
     * Resolved per-side borders after the full precedence chain. Sides may
     * carry explicit no-paint markers (`style` = nil/none/zero or
     * `widthPoints <= 0`); a null side means nothing was specified anywhere.
     */
    val borders: CellBorders,
    /**
     * The subset of [borders] edges this cell is responsible for painting,
     * after the shared-edge ownership rule (a cell owns its top and left
     * edges; bottom/right edges are painted by the neighbouring cell or, on
     * the table boundary, by this cell). Covered placeholders are empty.
     */
    val paintBorders: CellBorders,
    /** Resolved cell padding per side in points; null side = renderer default. */
    val margins: CellBorders,
    val verticalAlign: VerticalCellAlign?,
    val horizontalAlign: TextAlignment?,
    val textDirection: String?,
    val spans: List<TextSpan>
)

/**
 * Converts a rich [BlockNode.Table] into a [TablePresentation].
 *
 * Pure and Compose-free: usable from JVM unit tests, the mobile renderer and
 * the print pipeline. Merge/grid normalization (gridSpan, vMerge) is
 * consolidated here so every consumer sees the same dense grid.
 */
object TablePresentationResolver {

    private val NO_PAINT_STYLES = setOf("nil", "none", "zero")

    /**
     * @param spansFor extracts styled text spans for a cell. The legacy
     *   adapter supplies its inline-walk; tests may pass `{ emptyList() }`.
     */
    fun resolve(
        table: BlockNode.Table,
        spansFor: (BlockNode.TableCell) -> List<TextSpan> = { emptyList() }
    ): TablePresentation {
        val anchors = placeAnchors(table)
        if (anchors.isEmpty()) {
            return TablePresentation(
                columnCount = 0,
                widthPoints = table.style.widthPoints,
                alignment = table.style.alignment,
                shading = table.style.shading,
                borders = table.style.borders,
                insideBorders = table.style.insideBorders,
                cellMargins = table.style.cellMargins,
                layout = table.style.layout,
                gridColumnWidthsPt = table.gridColumns,
                caption = table.style.caption,
                description = table.style.description,
                rows = emptyList()
            )
        }

        val rowCount = table.rows.size
        val columnCount = anchors.maxOf { it.col + it.colSpan }
        val grid = buildGrid(anchors, rowCount, columnCount)

        val rows = table.rows.mapIndexed { rowIndex, row ->
            val cells = (0 until columnCount).map { column ->
                val anchor = grid[rowIndex][column]
                when {
                    anchor == null -> emptyCell(column)
                    // Positions inside an anchor's span (h or v) are covered.
                    anchor.covered || anchor.row != rowIndex || anchor.col != column ->
                        coveredCell(column)
                    else -> resolveCell(
                        table = table.style,
                        anchor = anchor,
                        rowIndex = rowIndex,
                        rowCount = rowCount,
                        columnCount = columnCount,
                        grid = grid,
                        spans = spansFor(anchor.cell)
                    )
                }
            }
            TableRowPresentation(
                isHeader = row.isHeader,
                heightPoints = row.heightPoints,
                cells = cells
            )
        }

        return TablePresentation(
            columnCount = columnCount,
            widthPoints = table.style.widthPoints,
            alignment = table.style.alignment,
            shading = table.style.shading,
            borders = table.style.borders,
            insideBorders = table.style.insideBorders,
            cellMargins = table.style.cellMargins,
            layout = table.style.layout,
            gridColumnWidthsPt = table.gridColumns,
            caption = table.style.caption,
            description = table.style.description,
            rows = rows
        )
    }

    // ------------------------------------------------------------------
    // Grid / merge normalization
    // ------------------------------------------------------------------

    private class Anchor(
        val row: Int,
        val cellIndex: Int,
        val cell: BlockNode.TableCell,
        val col: Int,
        val colSpan: Int,
        var rowSpan: Int = 1,
        var covered: Boolean = false,
        /** Row in which [rowSpan] was last extended (dedupe multi-column continues). */
        var lastExtendedRow: Int = -1
    ) {
        val bottomRow: Int get() = row + rowSpan - 1
        val rightCol: Int get() = col + colSpan - 1
    }

    /**
     * Places every logical cell on the grid, resolving `gridSpan` and
     * `vMerge` (RESTART/CONTINUE) into anchor spans and covered positions.
     */
    private fun placeAnchors(table: BlockNode.Table): List<Anchor> {
        val anchors = mutableListOf<Anchor>()
        // Start column of each cell per row (advancing by grid span).
        val startColumns = table.rows.map { row ->
            var column = 0
            row.cells.map { cell ->
                val start = column
                column += cell.style.gridSpan.coerceAtLeast(1)
                start
            }
        }
        val columnCount = maxOf(
            table.rows.maxOfOrNull { row -> row.cells.sumOf { it.style.gridSpan.coerceAtLeast(1) } } ?: 0,
            table.gridColumns.size,
            0
        )
        if (columnCount == 0) return emptyList()

        // occupancy[row][col] -> anchor covering that position (for neighbour lookups)
        val occupancy = List(table.rows.size) { arrayOfNulls<Anchor>(columnCount) }
        val active = mutableMapOf<Int, Anchor>()

        fun close(column: Int) {
            active.remove(column)
        }

        table.rows.forEachIndexed { rowIndex, row ->
            val seenColumns = mutableSetOf<Int>()
            row.cells.forEachIndexed { cellIndex, cell ->
                val col = startColumns[rowIndex].getOrElse(cellIndex) { seenColumns.size }
                if (col >= columnCount) {
                    seenColumns += col
                    return@forEachIndexed
                }
                val span = cell.style.gridSpan.coerceIn(1, columnCount - col)
                when (cell.style.verticalMerge) {
                    VerticalMerge.CONTINUE -> {
                        val anchor = active[col]
                        if (anchor != null && anchor.lastExtendedRow != rowIndex) {
                            anchor.rowSpan++
                            anchor.lastExtendedRow = rowIndex
                            val covered = Anchor(
                                row = rowIndex, cellIndex = cellIndex, cell = cell,
                                col = col, colSpan = span, covered = true
                            )
                            anchors += covered
                            mark(occupancy, rowIndex, col, span, anchor, columnCount)
                        } else {
                            // Orphaned continuation: render as an ordinary cell.
                            val orphan = Anchor(
                                row = rowIndex, cellIndex = cellIndex, cell = cell,
                                col = col, colSpan = span
                            )
                            anchors += orphan
                            mark(occupancy, rowIndex, col, span, orphan, columnCount)
                        }
                    }

                    VerticalMerge.RESTART -> {
                        close(col)
                        val anchor = Anchor(
                            row = rowIndex, cellIndex = cellIndex, cell = cell,
                            col = col, colSpan = span
                        )
                        anchors += anchor
                        active[col] = anchor
                        mark(occupancy, rowIndex, col, span, anchor, columnCount)
                    }

                    VerticalMerge.NONE -> {
                        close(col)
                        val anchor = Anchor(
                            row = rowIndex, cellIndex = cellIndex, cell = cell,
                            col = col, colSpan = span
                        )
                        anchors += anchor
                        mark(occupancy, rowIndex, col, span, anchor, columnCount)
                    }
                }
                seenColumns += col
            }
            // Columns no longer carrying a merge continuation close their span.
            active.keys.toList().filterNot { it in seenColumns }.forEach(::close)
        }
        return anchors
    }

    private fun mark(
        occupancy: List<Array<Anchor?>>,
        row: Int,
        col: Int,
        span: Int,
        anchor: Anchor,
        columnCount: Int
    ) {
        for (c in col until (col + span).coerceAtMost(columnCount)) {
            occupancy[row][c] = anchor
        }
    }

    /** Dense grid of anchors per (row, column); null = empty grid position. */
    private fun buildGrid(
        anchors: List<Anchor>,
        rowCount: Int,
        columnCount: Int
    ): Array<Array<Anchor?>> {
        val grid = Array(rowCount) { arrayOfNulls<Anchor>(columnCount) }
        anchors.forEach { anchor ->
            for (r in anchor.row..anchor.bottomRow.coerceAtMost(rowCount - 1)) {
                for (c in anchor.col..anchor.rightCol.coerceAtMost(columnCount - 1)) {
                    grid[r][c] = anchor
                }
            }
        }
        return grid
    }

    // ------------------------------------------------------------------
    // Style resolution
    // ------------------------------------------------------------------

    private fun resolveCell(
        table: TableStyle,
        anchor: Anchor,
        rowIndex: Int,
        rowCount: Int,
        columnCount: Int,
        grid: Array<Array<Anchor?>>,
        spans: List<TextSpan>
    ): TableCellPresentation {
        val cell = anchor.cell
        val style = cell.style
        val regions = applicableRegions(table, anchor, rowIndex, rowCount, columnCount)

        val shading = style.shading
            ?: regions.firstNotNullOfOrNull { it.shading }

        val borders = CellBorders(
            top = resolveBorderSide(table, regions, style, TableBorderSide.TOP, anchor, exterior = anchor.row == 0),
            right = resolveBorderSide(table, regions, style, TableBorderSide.RIGHT, anchor, exterior = anchor.rightCol == columnCount - 1),
            bottom = resolveBorderSide(table, regions, style, TableBorderSide.BOTTOM, anchor, exterior = anchor.bottomRow == rowCount - 1),
            left = resolveBorderSide(table, regions, style, TableBorderSide.LEFT, anchor, exterior = anchor.col == 0)
        )

        val paintBorders = CellBorders(
            top = paintable(edgeTop(table, anchor, rowIndex, columnCount, grid, borders)),
            right = paintable(if (anchor.rightCol == columnCount - 1) borders.right else null),
            bottom = paintable(if (anchor.bottomRow == rowCount - 1) borders.bottom else null),
            left = paintable(edgeLeft(table, anchor, rowIndex, columnCount, grid, borders))
        )

        val margins = resolveMargins(table, regions, style, borders)

        val horizontalAlign = cell.blocks.firstNotNullOfOrNull { block ->
            when (block) {
                is BlockNode.Paragraph -> block.style.alignment
                is BlockNode.Heading -> block.style.alignment
                else -> null
            }
        } ?: regions.firstNotNullOfOrNull { it.paragraphStyle?.alignment }

        return TableCellPresentation(
            columnIndex = anchor.col,
            colSpan = anchor.colSpan,
            rowSpan = anchor.rowSpan,
            isCovered = false,
            widthPoints = style.widthPoints,
            shading = shading,
            borders = borders,
            paintBorders = paintBorders,
            margins = margins,
            verticalAlign = style.verticalAlign,
            horizontalAlign = horizontalAlign,
            textDirection = style.textDirection,
            spans = spans
        )
    }

    /**
     * Region precedence follows the OOXML `w:tblStylePr` ordering: corner
     * cells, then first/last row/column, then horizontal and vertical bands.
     * Earlier regions win per property.
     */
    private fun applicableRegions(
        table: TableStyle,
        anchor: Anchor,
        rowIndex: Int,
        rowCount: Int,
        columnCount: Int
    ): List<TableRegionStyle> {
        if (table.regions.isEmpty()) return emptyList()
        val firstRow = rowIndex == 0
        val lastRow = anchor.bottomRow == rowCount - 1
        val firstCol = anchor.col == 0
        val lastCol = anchor.rightCol == columnCount - 1
        val band1Horz = rowIndex % 2 == 0
        val band2Horz = rowIndex % 2 == 1
        val band1Vert = anchor.col % 2 == 0
        val band2Vert = anchor.col % 2 == 1

        val order = buildList {
            if (firstRow && firstCol) add(TableRegion.NORTHWEST_CELL)
            if (firstRow && lastCol) add(TableRegion.NORTHEAST_CELL)
            if (lastRow && firstCol) add(TableRegion.SOUTHWEST_CELL)
            if (lastRow && lastCol) add(TableRegion.SOUTHEAST_CELL)
            if (firstRow) add(TableRegion.FIRST_ROW)
            if (lastRow) add(TableRegion.LAST_ROW)
            if (firstCol) add(TableRegion.FIRST_COLUMN)
            if (lastCol) add(TableRegion.LAST_COLUMN)
            if (band1Horz) add(TableRegion.BAND1_HORIZONTAL)
            if (band2Horz) add(TableRegion.BAND2_HORIZONTAL)
            if (band1Vert) add(TableRegion.BAND1_VERTICAL)
            if (band2Vert) add(TableRegion.BAND2_VERTICAL)
        }
        return order.mapNotNull(table.regions::get)
    }

    /**
     * Resolves one border side: cell direct > conditional regions > table
     * level. Table-level lookup uses [exterior] to choose outer borders vs
     * `insideH`/`insideV` interior borders.
     */
    private fun resolveBorderSide(
        table: TableStyle,
        regions: List<TableRegionStyle>,
        cell: CellStyle,
        side: TableBorderSide,
        anchor: Anchor,
        exterior: Boolean
    ): Border? {
        cell.borders.side(side)?.let { return it }
        regions.firstNotNullOfOrNull { it.borders.side(side) }?.let { return it }
        if (exterior) return table.borders.side(side)
        // insideBorders maps top/bottom -> insideH and left/right -> insideV,
        // so the same side lookup yields the correct interior border. Null inside
        // borders fall back to the outer table borders so table-level border
        // definitions (e.g. ODT fo:border or DOCX outer tblBorders) apply to
        // interior cells when no explicit inside border is specified.
        return table.insideBorders?.side(side) ?: table.borders.side(side)
    }

    /** Top edge: own resolved top, else the bottom border of the cell above. */
    private fun edgeTop(
        table: TableStyle,
        anchor: Anchor,
        rowIndex: Int,
        columnCount: Int,
        grid: Array<Array<Anchor?>>,
        borders: CellBorders
    ): Border? {
        borders.top?.let { return it }
        if (rowIndex == 0) return null
        val above = grid[rowIndex - 1][anchor.col] ?: return null
        if (above === anchor || above.covered) {
            // Covered positions belong to the same anchor; no shared edge here.
            return null
        }
        return aboveResolvedBottom(above, table, grid, rowIndex - 1, columnCount)
    }

    private fun aboveResolvedBottom(
        above: Anchor,
        table: TableStyle,
        grid: Array<Array<Anchor?>>,
        rowIndex: Int,
        columnCount: Int
    ): Border? {
        // Recompute the above anchor's bottom border in its own context.
        val rowCount = grid.size
        val regions = applicableRegions(table, above, rowIndex, rowCount, columnCount)
        return resolveBorderSide(
            table, regions, above.cell.style, TableBorderSide.BOTTOM, above,
            exterior = above.bottomRow == rowCount - 1
        )
    }

    /** Left edge: own resolved left, else the right border of the left neighbour. */
    private fun edgeLeft(
        table: TableStyle,
        anchor: Anchor,
        rowIndex: Int,
        columnCount: Int,
        grid: Array<Array<Anchor?>>,
        borders: CellBorders
    ): Border? {
        borders.left?.let { return it }
        if (anchor.col == 0) return null
        val left = grid[rowIndex][anchor.col - 1] ?: return null
        if (left === anchor || left.covered) return null
        val regions = applicableRegions(table, left, rowIndex, grid.size, columnCount)
        return resolveBorderSide(
            table, regions, left.cell.style, TableBorderSide.RIGHT, left,
            exterior = left.rightCol == columnCount - 1
        )
    }

    /** Converts a resolved border to a paint instruction; null = do not draw. */
    private fun paintable(border: Border?): Border? {
        if (border == null) return null
        val style = border.style?.lowercase()
        if (style != null && style in NO_PAINT_STYLES) return null
        if (border.widthPoints != null && border.widthPoints <= 0.0) return null
        return border
    }

    /**
     * Resolves cell padding per side. A border's `space` (OOXML `w:space`,
     * the gap between the border stroke and cell content) is folded into the
     * side's padding so both renderers honour it without extra plumbing.
     */
    private fun resolveMargins(
        table: TableStyle,
        regions: List<TableRegionStyle>,
        cell: CellStyle,
        borders: CellBorders
    ): CellBorders {
        fun side(s: TableBorderSide): Border? {
            // Border spacing only applies to borders that can actually paint.
            val border = borders.side(s)
            val borderStyle = border?.style?.lowercase()
            val space = if (
                border != null &&
                (borderStyle == null || borderStyle !in NO_PAINT_STYLES) &&
                (border.widthPoints == null || border.widthPoints!! > 0.0)
            ) border.space ?: 0.0 else 0.0
            cell.margins?.side(s)?.widthPoints?.let { return Border(widthPoints = it + space) }
            regions.firstNotNullOfOrNull { it.cellMargins?.side(s)?.widthPoints }?.let {
                return Border(widthPoints = it + space)
            }
            return table.cellMargins?.side(s)?.widthPoints?.let { Border(widthPoints = it + space) }
        }
        return CellBorders(
            top = side(TableBorderSide.TOP),
            right = side(TableBorderSide.RIGHT),
            bottom = side(TableBorderSide.BOTTOM),
            left = side(TableBorderSide.LEFT)
        )
    }

    private fun emptyCell(column: Int): TableCellPresentation = TableCellPresentation(
        columnIndex = column,
        colSpan = 1,
        rowSpan = 1,
        isCovered = false,
        widthPoints = null,
        shading = null,
        borders = CellBorders(),
        paintBorders = CellBorders(),
        margins = CellBorders(),
        verticalAlign = null,
        horizontalAlign = null,
        textDirection = null,
        spans = emptyList()
    )

    private fun coveredCell(column: Int): TableCellPresentation =
        emptyCell(column).copy(isCovered = true)
}
