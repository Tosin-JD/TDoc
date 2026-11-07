package com.tosin.docprocessor.domain.document.model.style

enum class VerticalCellAlign { TOP, CENTER, BOTTOM }

/** Conditional-formatting banding flags resolved from a DOCX table style. */
data class TableBandFlags(
    val firstRow: Boolean = false,
    val lastRow: Boolean = false,
    val firstColumn: Boolean = false,
    val lastColumn: Boolean = false,
    val band1: Boolean = false,
    val band2: Boolean = false
) {
    /** Inheritance merge: a band enabled by any style in the chain stays enabled. */
    fun mergedWith(other: TableBandFlags): TableBandFlags = TableBandFlags(
        firstRow = firstRow || other.firstRow,
        lastRow = lastRow || other.lastRow,
        firstColumn = firstColumn || other.firstColumn,
        lastColumn = lastColumn || other.lastColumn,
        band1 = band1 || other.band1,
        band2 = band2 || other.band2
    )
}

/**
 * A `w:tblStylePr` conditional-formatting region of a DOCX table style.
 * Properties declared inside the region apply only to cells matching the
 * region's row/column position (first row, banded rows, corner cells, …).
 */
enum class TableRegion {
    FIRST_ROW,
    LAST_ROW,
    FIRST_COLUMN,
    LAST_COLUMN,
    BAND1_HORIZONTAL,
    BAND2_HORIZONTAL,
    BAND1_VERTICAL,
    BAND2_VERTICAL,
    NORTHWEST_CELL,
    NORTHEAST_CELL,
    SOUTHWEST_CELL,
    SOUTHEAST_CELL;

    companion object {
        /** Maps a `w:tblStylePr/@w:type` value to a region; null when unknown. */
        fun fromOoxml(type: String?): TableRegion? = when (type?.lowercase()) {
            "firstrow" -> FIRST_ROW
            "lastrow" -> LAST_ROW
            "firstcol" -> FIRST_COLUMN
            "lastcol" -> LAST_COLUMN
            "band1horz" -> BAND1_HORIZONTAL
            "band2horz" -> BAND2_HORIZONTAL
            "band1vert" -> BAND1_VERTICAL
            "band2vert" -> BAND2_VERTICAL
            "nwcell" -> NORTHWEST_CELL
            "necell" -> NORTHEAST_CELL
            "swcell" -> SOUTHWEST_CELL
            "secell" -> SOUTHEAST_CELL
            else -> null
        }
    }
}

/**
 * Resolved paintable properties of one conditional table-style region.
 * `runStyle`/`paragraphStyle` are preserved for completeness; renderers may
 * apply them to cell text where supported.
 */
data class TableRegionStyle(
    val shading: Shading? = null,
    val borders: CellBorders = CellBorders(),
    val cellMargins: CellBorders? = null,
    val runStyle: TextStyle? = null,
    val paragraphStyle: ParagraphStyle? = null
) {
    /** Inheritance merge for `w:tblStylePr` regions along a `basedOn` chain. */
    fun mergedWith(other: TableRegionStyle): TableRegionStyle {
        val mergedRun = when {
            runStyle != null && other.runStyle != null -> runStyle!!.mergedWith(other.runStyle!!)
            else -> other.runStyle ?: runStyle
        }
        val mergedParagraph = when {
            paragraphStyle != null && other.paragraphStyle != null ->
                paragraphStyle!!.mergedWith(other.paragraphStyle!!)
            else -> other.paragraphStyle ?: paragraphStyle
        }
        return TableRegionStyle(
            shading = other.shading ?: shading,
            borders = borders.overlay(other.borders),
            cellMargins = mergeCellBorders(cellMargins, other.cellMargins),
            runStyle = mergedRun,
            paragraphStyle = mergedParagraph
        )
    }
}

data class CellBorders(
    val top: Border? = null,
    val right: Border? = null,
    val bottom: Border? = null,
    val left: Border? = null
) {
    /** True when at least one side carries a value (including explicit none). */
    fun hasAny(): Boolean = top != null || right != null || bottom != null || left != null

    /**
     * Per-side overlay: [other] wins where a side is specified, [this] supplies
     * the remaining sides. Used to merge direct formatting over inherited
     * style formatting without losing unspecified sides.
     */
    fun overlay(other: CellBorders?): CellBorders {
        if (other == null) return this
        return CellBorders(
            top = other.top ?: top,
            right = other.right ?: right,
            bottom = other.bottom ?: bottom,
            left = other.left ?: left
        )
    }

    fun side(side: TableBorderSide): Border? = when (side) {
        TableBorderSide.TOP -> top
        TableBorderSide.RIGHT -> right
        TableBorderSide.BOTTOM -> bottom
        TableBorderSide.LEFT -> left
    }

    fun withSide(side: TableBorderSide, border: Border?): CellBorders = when (side) {
        TableBorderSide.TOP -> copy(top = border)
        TableBorderSide.RIGHT -> copy(right = border)
        TableBorderSide.BOTTOM -> copy(bottom = border)
        TableBorderSide.LEFT -> copy(left = border)
    }
}

enum class TableBorderSide { TOP, RIGHT, BOTTOM, LEFT }

/** Per-side merge for nullable [CellBorders] (inside borders, cell margins). */
internal fun mergeCellBorders(base: CellBorders?, over: CellBorders?): CellBorders? = when {
    over == null -> base
    base == null -> over
    else -> base.overlay(over)
}

data class TableStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val widthPoints: Double? = null,
    val alignment: TextAlignment? = null,
    val borders: CellBorders = CellBorders(),
    /** Interior (insideH/insideV) borders; null sides fall back to [borders]. */
    val insideBorders: CellBorders? = null,
    val shading: Shading? = null,
    val cellMargins: CellBorders? = null,
    val layout: String? = null,
    val caption: String? = null,
    val description: String? = null,
    val banding: TableBandFlags = TableBandFlags(),
    /** Resolved `w:tblStylePr` conditional regions keyed by region kind. */
    val regions: Map<TableRegion, TableRegionStyle> = emptyMap()
) {
    /**
     * Style-inheritance merge: [other] (the more specific style) wins per
     * field; borders and margins merge per side so an unspecified side keeps
     * the inherited value while an explicit `nil`/`none` side still wins.
     */
    fun mergedWith(other: TableStyle): TableStyle {
        val mergedRegions = if (regions.isEmpty()) {
            other.regions
        } else if (other.regions.isEmpty()) {
            regions
        } else {
            regions.toMutableMap().also { result ->
                other.regions.forEach { (region, style) ->
                    result[region] = result[region]?.mergedWith(style) ?: style
                }
            }
        }
        return TableStyle(
            styleId = other.styleId ?: styleId,
            styleName = other.styleName ?: styleName,
            widthPoints = other.widthPoints ?: widthPoints,
            alignment = other.alignment ?: alignment,
            borders = borders.overlay(other.borders),
            insideBorders = mergeCellBorders(insideBorders, other.insideBorders),
            shading = other.shading ?: shading,
            cellMargins = mergeCellBorders(cellMargins, other.cellMargins),
            layout = other.layout ?: layout,
            caption = other.caption ?: caption,
            description = other.description ?: description,
            banding = banding.mergedWith(other.banding),
            regions = mergedRegions
        )
    }
}

data class CellStyle(
    val widthPoints: Double? = null,
    val gridSpan: Int = 1,
    val verticalMerge: VerticalMerge = VerticalMerge.NONE,
    val borders: CellBorders = CellBorders(),
    val shading: Shading? = null,
    val verticalAlign: VerticalCellAlign? = null,
    val textDirection: String? = null,
    val margins: CellBorders? = null
) {
    /**
     * Style-inheritance merge (`style:parent-style-name` chains): [other] wins
     * per field; borders and margins merge per side. `gridSpan`/`verticalMerge`
     * are structural and only carried when explicitly set.
     */
    fun mergedWith(other: CellStyle): CellStyle = CellStyle(
        widthPoints = other.widthPoints ?: widthPoints,
        gridSpan = if (other.gridSpan != 1) other.gridSpan else gridSpan,
        verticalMerge = if (other.verticalMerge != VerticalMerge.NONE) other.verticalMerge else verticalMerge,
        borders = borders.overlay(other.borders),
        shading = other.shading ?: shading,
        verticalAlign = other.verticalAlign ?: verticalAlign,
        textDirection = other.textDirection ?: textDirection,
        margins = mergeCellBorders(margins, other.margins)
    )
}

enum class VerticalMerge { NONE, RESTART, CONTINUE }
