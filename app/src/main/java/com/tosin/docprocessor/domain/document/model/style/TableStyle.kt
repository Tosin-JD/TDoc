package com.tosin.docprocessor.domain.document.model.style

enum class VerticalCellAlign { TOP, CENTER, BOTTOM }

data class CellBorders(
    val top: Border? = null,
    val right: Border? = null,
    val bottom: Border? = null,
    val left: Border? = null
)

data class TableStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val widthPoints: Double? = null,
    val alignment: TextAlignment? = null,
    val borders: CellBorders = CellBorders(),
    val shading: Shading? = null,
    val cellMargins: CellBorders? = null,
    val layout: String? = null,
    val caption: String? = null,
    val description: String? = null
)

data class CellStyle(
    val widthPoints: Double? = null,
    val gridSpan: Int = 1,
    val verticalMerge: VerticalMerge = VerticalMerge.NONE,
    val borders: CellBorders = CellBorders(),
    val shading: Shading? = null,
    val verticalAlign: VerticalCellAlign? = null,
    val textDirection: String? = null,
    val margins: CellBorders? = null
)

enum class VerticalMerge { NONE, RESTART, CONTINUE }
