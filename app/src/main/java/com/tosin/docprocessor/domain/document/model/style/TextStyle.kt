package com.tosin.docprocessor.domain.document.model.style

enum class VerticalAlign { BASELINE, SUPERSCRIPT, SUBSCRIPT }

enum class UnderlineKind { NONE, SINGLE, DOUBLE, DOTTED, DASHED, WAVE }

data class Border(
    val style: String? = null,
    val widthPoints: Double? = null,
    val color: String? = null,
    val space: Double? = null
)

data class Shading(
    val fill: String? = null,
    val color: String? = null,
    val pattern: String? = null
)

/**
 * Character/run formatting. `null` means "not specified here" (may still be
 * inherited); explicit `false` is distinct from absent.
 */
data class TextStyle(
    val fontAscii: String? = null,
    val fontHAnsi: String? = null,
    val fontEastAsia: String? = null,
    val fontComplexScript: String? = null,
    val fontSizePoints: Double? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: UnderlineKind? = null,
    val underlineColor: String? = null,
    val strike: Boolean? = null,
    val verticalAlign: VerticalAlign? = null,
    val color: String? = null,
    val colorTheme: String? = null,
    val colorThemeTint: String? = null,
    val colorThemeShade: String? = null,
    val highlight: String? = null,
    val shading: Shading? = null,
    val caps: Boolean? = null,
    val smallCaps: Boolean? = null,
    val letterSpacingPoints: Double? = null,
    val kerningPoints: Double? = null,
    val positionPoints: Double? = null,
    val language: String? = null,
    val rightToLeft: Boolean? = null,
    val direction: String? = null,
    val hidden: Boolean? = null
) {
    /** Overlay [other] on top of this style (other wins where specified). */
    fun mergedWith(other: TextStyle): TextStyle = TextStyle(
        fontAscii = other.fontAscii ?: fontAscii,
        fontHAnsi = other.fontHAnsi ?: fontHAnsi,
        fontEastAsia = other.fontEastAsia ?: fontEastAsia,
        fontComplexScript = other.fontComplexScript ?: fontComplexScript,
        fontSizePoints = other.fontSizePoints ?: fontSizePoints,
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        underline = other.underline ?: underline,
        underlineColor = other.underlineColor ?: underlineColor,
        strike = other.strike ?: strike,
        verticalAlign = other.verticalAlign ?: verticalAlign,
        color = other.color ?: color,
        colorTheme = other.colorTheme ?: colorTheme,
        colorThemeTint = other.colorThemeTint ?: colorThemeTint,
        colorThemeShade = other.colorThemeShade ?: colorThemeShade,
        highlight = other.highlight ?: highlight,
        shading = other.shading ?: shading,
        caps = other.caps ?: caps,
        smallCaps = other.smallCaps ?: smallCaps,
        letterSpacingPoints = other.letterSpacingPoints ?: letterSpacingPoints,
        kerningPoints = other.kerningPoints ?: kerningPoints,
        positionPoints = other.positionPoints ?: positionPoints,
        language = other.language ?: language,
        rightToLeft = other.rightToLeft ?: rightToLeft,
        direction = other.direction ?: direction,
        hidden = other.hidden ?: hidden
    )
}
