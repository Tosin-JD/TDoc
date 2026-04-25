package com.tosin.docprocessor.data.parser.internal.models

data class TextSpan(
    val text: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val color: String
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