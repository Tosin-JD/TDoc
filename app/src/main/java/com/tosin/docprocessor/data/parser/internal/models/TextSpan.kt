package com.tosin.docprocessor.data.parser.internal.models

data class TextSpan(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val isHidden: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val color: String = "000000",
    val highlightColor: String? = null,
    val characterSpacing: Int? = null,
    val language: String? = null,
    val hasShadow: Boolean = false,
    val hasOutline: Boolean = false,
    val isEmbossed: Boolean = false,
    val isEngraved: Boolean = false
) {
    constructor(
        text: String?,
        isBold: Boolean?,
        isItalic: Boolean?,
        color: String?
    ) : this(
        text = text ?: "",
        isBold = isBold ?: false,
        isItalic = isItalic ?: false,
        color = color ?: "000000"
    )
}
