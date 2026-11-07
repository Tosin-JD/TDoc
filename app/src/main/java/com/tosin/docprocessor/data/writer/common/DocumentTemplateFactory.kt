package com.tosin.docprocessor.data.writer.common

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.document.model.StyleCatalogue
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import com.tosin.docprocessor.domain.document.model.style.PageStyle
import com.tosin.docprocessor.domain.document.model.style.PageMargins
import com.tosin.docprocessor.domain.document.model.style.TextStyle

/**
 * Produces a minimal but spec-compliant [ParsedDocument] for newly created
 * DOCX and ODT files.
 *
 * A document generated from an empty [ParsedDocument()] lacks:
 *  - A required terminal `w:p` in `w:body` → Word/LibreOffice reject the file.
 *  - Base paragraph and character styles → style references in content fail.
 *  - A section definition → page dimensions are unknown.
 *
 * [createDefault] addresses all three issues.
 */
object DocumentTemplateFactory {

    /** A4 page width in points (210 mm). */
    private const val A4_WIDTH_PT = 595.28
    /** A4 page height in points (297 mm). */
    private const val A4_HEIGHT_PT = 841.89
    /** Standard 1-inch margin in points. */
    private const val MARGIN_1_INCH_PT = 72.0

    /**
     * Creates a minimal valid [ParsedDocument] appropriate for [mimeType].
     *
     * @param mimeType  MIME type of the target format (DOCX or ODT).
     * @return          A [ParsedDocument] with one empty paragraph, default
     *                  styles, and an A4 section so the DOCX/ODT body is
     *                  well-formed.
     */
    fun createDefault(mimeType: String): ParsedDocument {
        val sourceFormat = when {
            mimeType.contains("wordprocessingml") || mimeType.endsWith("docx") -> SourceFormat.DOCX
            mimeType.contains("opendocument") || mimeType.endsWith("odt") -> SourceFormat.ODT
            else -> SourceFormat.UNKNOWN
        }

        val defaultParagraphStyle = ParagraphStyle(
            styleId = "Normal",
            styleName = "Normal"
        )
        val defaultTextStyle = TextStyle(
            fontSizePoints = 11.0
        )
        val styles = StyleCatalogue(
            defaultParagraphStyle = defaultParagraphStyle,
            defaultTextStyle = defaultTextStyle,
            paragraphStyles = mapOf("Normal" to defaultParagraphStyle)
        )

        val page = PageStyle(
            widthPoints = A4_WIDTH_PT,
            heightPoints = A4_HEIGHT_PT,
            margins = PageMargins(
                topPoints = MARGIN_1_INCH_PT,
                rightPoints = MARGIN_1_INCH_PT,
                bottomPoints = MARGIN_1_INCH_PT,
                leftPoints = MARGIN_1_INCH_PT
            )
        )
        val section = SectionStyle(page = page)

        // ISO/IEC 29500-1 §17.18.2: a w:body must contain at least one w:p.
        val emptyParagraph = BlockNode.Paragraph(
            inlines = listOf(InlineNode.Text("")),
            style = defaultParagraphStyle
        )

        return ParsedDocument(
            blocks = listOf(emptyParagraph),
            styles = styles,
            sections = listOf(section),
            sourceFormat = sourceFormat
        )
    }
}

