package com.tosin.docprocessor.domain.document.model.style

enum class PageOrientation { PORTRAIT, LANDSCAPE }

data class PageMargins(
    val topPoints: Double? = null,
    val rightPoints: Double? = null,
    val bottomPoints: Double? = null,
    val leftPoints: Double? = null,
    val headerPoints: Double? = null,
    val footerPoints: Double? = null,
    val gutterPoints: Double? = null
)

data class PageStyle(
    val widthPoints: Double? = null,
    val heightPoints: Double? = null,
    val orientation: PageOrientation? = null,
    val margins: PageMargins = PageMargins(),
    val columns: Int? = null,
    val columnSpacingPoints: Double? = null,
    /** Explicit column widths when the layout is not equal-width. */
    val columnWidths: List<Double?> = emptyList(),
    /** Vertical alignment of the page body: `top` / `center` / `both`. */
    val verticalAlign: String? = null,
    val pageBorders: CellBorders? = null,
    val lineNumberingStep: Int? = null,
    /** Footnote/endnote placement relative to the body (`topBottom`, `pageBottom`). */
    val notePlacement: String? = null,
    /** Page background color (hex or CSS name) when declared. */
    val background: String? = null,
    /** Logical resource id for a page background image. */
    val backgroundImageResourceId: String? = null
)

enum class HeaderFooterVariant { DEFAULT, FIRST, EVEN }

data class SectionStyle(
    val index: Int = 0,
    val type: String? = null,
    val page: PageStyle = PageStyle(),
    val pageNumberStart: Int? = null,
    val hasDifferentFirstPage: Boolean = false,
    val hasDifferentEvenOdd: Boolean = false,
    /** ODF master-page name associated with this section. */
    val masterPageName: String? = null
)
