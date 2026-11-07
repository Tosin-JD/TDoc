package com.tosin.docprocessor.ui.components.table.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign

/**
 * Immutable, presentation-ready representation of a document table.
 *
 * Produced by [com.tosin.docprocessor.ui.components.table.TableUiMapper] so the
 * Compose layer never has to reason about ragged source rows. All paint
 * properties are document-authored; the renderer must not substitute
 * application theme styling for absent values.
 *
 * @property tableBackground  Table-level background shading from the document's
 *   `w:tblPr/w:shd` or ODF equivalent. When null no background is painted,
 *   revealing the page/canvas colour beneath.
 * @property outerBorders     Table outer border specification used by the
 *   legacy (non-presentation) mapping path. Null = no explicit outer border.
 * @property sourceWidthsPt   Source grid column widths in points. When present
 *   the column sizer prefers these proportions over content heuristics.
 */
@Immutable
data class TableUiModel(
    val columnCount: Int,
    val rows: List<TableRowUi>,
    val columnWeights: List<Float>,
    /** Document-specified table background shading colour. Null = no shading. */
    val tableBackground: Color? = null,
    /** Raw table shading source (hex/named) for theme-aware re-resolution. */
    val tableBackgroundSource: String? = null,
    /** Document-specified outer table borders. Null = no explicit outer border. */
    val outerBorders: CellBorders? = null,
    /** True only when the document style requests row banding. */
    val isStriped: Boolean = false,
    /** Source grid widths in points (from `w:tblGrid`/ODF columns). */
    val sourceWidthsPt: List<Float> = emptyList(),
    /**
     * Document-authored table width in points (`w:tblW`/ODF width). When the
     * table is narrower than the viewport it is sized to this width instead of
     * stretching; null = fill-or-scroll behaviour.
     */
    val preferredWidthPt: Float? = null,
    /** Document-authored table alignment used when [preferredWidthPt] applies. */
    val tableAlignment: CellTextAlignment = CellTextAlignment.START
) {
    val isEmpty: Boolean get() = rows.isEmpty() || columnCount == 0
}

@Immutable
data class TableRowUi(
    val cells: List<TableCellUi>,
    val isHeader: Boolean
)

@Immutable
data class TableCellUi(
    val text: String,
    val alignment: CellTextAlignment = CellTextAlignment.START,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val isCovered: Boolean = false,
    val backgroundColor: Color? = null,
    /** Raw cell shading source for theme-aware re-resolution. */
    val backgroundColorSource: String? = null,
    val spans: List<TextSpan> = emptyList(),
    /**
     * Per-cell borders to paint. When [paintBordersResolved] is true these are
     * already the final per-edge paint instructions (shared-edge ownership
     * applied); otherwise they are raw cell borders and [TableUiModel.outerBorders]
     * supplies the table-level fallback per side.
     */
    val borders: CellBorders? = null,
    /** True when [borders] is a fully resolved paint instruction set. */
    val paintBordersResolved: Boolean = false,
    /** Resolved cell padding/margins in points (`Border.widthPoints`). */
    val margins: CellBorders? = null,
    /** Vertical alignment of the cell content (`w:vAlign`). */
    val verticalAlignment: VerticalCellAlign = VerticalCellAlign.TOP
)

enum class CellTextAlignment { START, CENTER, END }
