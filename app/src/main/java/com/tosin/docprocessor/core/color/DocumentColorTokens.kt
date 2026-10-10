package com.tosin.docprocessor.core.color

/**
 * Theme-aware ARGB tokens used by document rendering outside Compose.
 *
 * This type deliberately lives in a neutral `core/color` package that depends on
 * nothing, so `domain`, `core.rendering` and `ui` can all consume it without
 * creating package cycles. Light/Dark values are starting points validated
 * against WCAG contrast (see [DocumentTextColorResolver]).
 */
data class DocumentColorTokens(
    val canvasBackground: Int,
    val pageBackground: Int,
    val contentText: Int,
    val secondaryText: Int,
    val tableBackground: Int,
    val tableHeaderBackground: Int,
    val tableBorder: Int,
    val tableCellBorder: Int,
    val placeholderBackground: Int,
    val placeholderBorder: Int
) {
    companion object {
        val Light = DocumentColorTokens(
            canvasBackground = 0xFFE4E4E4.toInt(),
            pageBackground = 0xFFFFFFFF.toInt(),
            contentText = 0xFF000000.toInt(),
            secondaryText = 0xFF6B6B6B.toInt(),
            tableBackground = 0xFFFFFFFF.toInt(),
            tableHeaderBackground = 0xFFF2F2F2.toInt(),
            tableBorder = 0xFF7F7F7F.toInt(),
            tableCellBorder = 0xFFB0B0B0.toInt(),
            placeholderBackground = 0xFFDADADA.toInt(),
            placeholderBorder = 0xFF8A8A8A.toInt()
        )

        val Dark = DocumentColorTokens(
            canvasBackground = 0xFF1E1E1E.toInt(),
            pageBackground = 0xFF2B2B2B.toInt(),
            contentText = 0xFFECECEC.toInt(),
            secondaryText = 0xFFBDBDBD.toInt(),
            tableBackground = 0xFF323232.toInt(),
            tableHeaderBackground = 0xFF3C3C3C.toInt(),
            tableBorder = 0xFF9A9A9A.toInt(),
            tableCellBorder = 0xFF5A5A5A.toInt(),
            placeholderBackground = 0xFF3A3A3A.toInt(),
            placeholderBorder = 0xFF9A9A9A.toInt()
        )
    }
}
