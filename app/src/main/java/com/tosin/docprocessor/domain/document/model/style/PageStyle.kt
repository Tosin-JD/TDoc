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
    val background: String? = null
)

enum class HeaderFooterVariant { DEFAULT, FIRST, EVEN }

data class SectionStyle(
    val index: Int = 0,
    val type: String? = null,
    val page: PageStyle = PageStyle(),
    val pageNumberStart: Int? = null,
    val hasDifferentFirstPage: Boolean = false,
    val hasDifferentEvenOdd: Boolean = false
)
