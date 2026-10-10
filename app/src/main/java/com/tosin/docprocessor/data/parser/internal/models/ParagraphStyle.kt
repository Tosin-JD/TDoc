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

/**
 * How [ParagraphSpacing.line] is interpreted.
 *
 * - [AUTO]: a multiple of the line, expressed in 240ths of a line (matches
 *   OOXML `w:line` with `w:lineRule="auto"`).
 * - [EXACT] / [AT_LEAST]: an absolute length in twips.
 */
enum class LineSpacingRule {
    AUTO,
    EXACT,
    AT_LEAST
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
    /**
     * Line spacing in OOXML units: 240ths of a line when [lineRule] is
     * [LineSpacingRule.AUTO], otherwise twips.
     */
    val line: Int? = null,
    val lineRule: LineSpacingRule? = null
)
