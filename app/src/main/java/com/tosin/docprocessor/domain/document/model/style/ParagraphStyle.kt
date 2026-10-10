package com.tosin.docprocessor.domain.document.model.style

enum class TextAlignment { START, END, CENTER, JUSTIFIED, DISTRIBUTED }

data class TabStop(
    val positionPoints: Double,
    val alignment: String? = null,
    val leader: String? = null
)

data class ParagraphStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val basedOn: String? = null,
    val next: String? = null,
    val alignment: TextAlignment? = null,
    val indentationStartPoints: Double? = null,
    val indentationEndPoints: Double? = null,
    val firstLineIndentPoints: Double? = null,
    val hangingIndentPoints: Double? = null,
    val spaceBeforePoints: Double? = null,
    val spaceAfterPoints: Double? = null,
    val lineSpacing: Double? = null,
    val lineSpacingRule: String? = null,
    val keepNext: Boolean? = null,
    val keepLines: Boolean? = null,
    val pageBreakBefore: Boolean? = null,
    val widowControl: Boolean? = null,
    val outlineLevel: Int? = null,
    val tabs: List<TabStop> = emptyList(),
    val borders: List<Border> = emptyList(),
    val shading: Shading? = null,
    val defaultRunStyle: TextStyle? = null
) {
    fun mergedWith(other: ParagraphStyle): ParagraphStyle = ParagraphStyle(
        styleId = other.styleId ?: styleId,
        styleName = other.styleName ?: styleName,
        basedOn = other.basedOn ?: basedOn,
        next = other.next ?: next,
        alignment = other.alignment ?: alignment,
        indentationStartPoints = other.indentationStartPoints ?: indentationStartPoints,
        indentationEndPoints = other.indentationEndPoints ?: indentationEndPoints,
        firstLineIndentPoints = other.firstLineIndentPoints ?: firstLineIndentPoints,
        hangingIndentPoints = other.hangingIndentPoints ?: hangingIndentPoints,
        spaceBeforePoints = other.spaceBeforePoints ?: spaceBeforePoints,
        spaceAfterPoints = other.spaceAfterPoints ?: spaceAfterPoints,
        lineSpacing = other.lineSpacing ?: lineSpacing,
        lineSpacingRule = other.lineSpacingRule ?: lineSpacingRule,
        keepNext = other.keepNext ?: keepNext,
        keepLines = other.keepLines ?: keepLines,
        pageBreakBefore = other.pageBreakBefore ?: pageBreakBefore,
        widowControl = other.widowControl ?: widowControl,
        outlineLevel = other.outlineLevel ?: outlineLevel,
        tabs = if (other.tabs.isNotEmpty()) other.tabs else tabs,
        borders = if (other.borders.isNotEmpty()) other.borders else borders,
        shading = other.shading ?: shading,
        defaultRunStyle = when {
            defaultRunStyle == null -> other.defaultRunStyle
            other.defaultRunStyle == null -> defaultRunStyle
            else -> defaultRunStyle.mergedWith(other.defaultRunStyle)
        }
    )
}
