package com.tosin.docprocessor.data.parser.internal.models

data class ParagraphStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val alignment: ParagraphAlignment = ParagraphAlignment.START,
    val indentation: ParagraphIndentation = ParagraphIndentation(),
    val spacing: ParagraphSpacing = ParagraphSpacing(),
    val outlineLevel: Int? = null,
    val isHeading: Boolean = false,
    val headingLevel: Int? = null
)

enum class ParagraphAlignment {
    START,
    END,
    CENTER,
    JUSTIFIED,
    DISTRIBUTED
}

data class ParagraphIndentation(
    val left: Int? = null,
    val right: Int? = null,
    val firstLine: Int? = null,
    val hanging: Int? = null
)

data class ParagraphSpacing(
    val before: Int? = null,
    val after: Int? = null,
    val line: Int? = null
)
