package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import org.apache.poi.xwpf.usermodel.ParagraphAlignment as PoiParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFParagraph

class DocxParagraphStyleParser {

    fun parse(paragraph: XWPFParagraph): ParagraphStyle {
        val properties = paragraph.ctp.pPr
        val styleId = paragraph.style
        val outlineLevel = properties?.outlineLvl?.`val`?.toInt()
        val headingLevel = extractHeadingLevel(styleId, outlineLevel)

        return ParagraphStyle(
            styleId = styleId,
            styleName = paragraph.styleID,
            alignment = paragraph.alignment.toParagraphAlignment(),
            indentation = ParagraphIndentation(
                left = paragraph.indentationLeft.takeIf { it != 0 },
                right = paragraph.indentationRight.takeIf { it != 0 },
                firstLine = paragraph.indentationFirstLine.takeIf { it != 0 },
                hanging = paragraph.indentationHanging.takeIf { it != 0 }
            ),
            spacing = ParagraphSpacing(
                before = paragraph.spacingBefore.takeIf { it != 0 },
                after = paragraph.spacingAfter.takeIf { it != 0 },
                line = paragraph.spacingBetween.takeIf { it > 0.0 }?.times(240)?.toInt()
            ),
            outlineLevel = outlineLevel,
            isHeading = headingLevel != null,
            headingLevel = headingLevel
        )
    }

    private fun extractHeadingLevel(styleId: String?, outlineLevel: Int?): Int? {
        val styleHeadingLevel = styleId
            ?.trim()
            ?.let { Regex("""Heading([1-9])""", RegexOption.IGNORE_CASE).matchEntire(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return styleHeadingLevel ?: outlineLevel?.plus(1)?.takeIf { it in 1..9 }
    }

    private fun PoiParagraphAlignment.toParagraphAlignment(): ParagraphAlignment =
        when (this) {
            PoiParagraphAlignment.RIGHT -> ParagraphAlignment.END
            PoiParagraphAlignment.CENTER -> ParagraphAlignment.CENTER
            PoiParagraphAlignment.BOTH -> ParagraphAlignment.JUSTIFIED
            PoiParagraphAlignment.DISTRIBUTE -> ParagraphAlignment.DISTRIBUTED
            else -> ParagraphAlignment.START
        }
}
