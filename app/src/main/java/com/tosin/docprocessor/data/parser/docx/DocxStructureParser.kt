package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterReference
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteKind
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import org.apache.poi.xwpf.usermodel.XWPFComment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFEndnote
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter
import org.apache.poi.xwpf.usermodel.XWPFFootnote
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr

class DocxStructureParser {

    fun parseDocumentLevelElements(document: XWPFDocument): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        output += parseSections(document)
        output += parseHeadersAndFooters(document)
        output += parseFootnotes(document)
        output += parseEndnotes(document)
        output += parseComments(document)
        return output
    }

    fun parseParagraphMetadata(paragraph: XWPFParagraph): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        output += paragraph.ctp.bookmarkStartList.map {
            DocumentElement.Bookmark(
                BookmarkInfo(
                    id = it.id.toString(),
                    name = it.name.orEmpty()
                )
            )
        }
        return output
    }

    private fun parseSections(document: XWPFDocument): List<DocumentElement.Section> {
        val sectionElements = mutableListOf<DocumentElement.Section>()
        var sectionIndex = 0

        document.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val sectionProperties = paragraph.ctp.pPr?.sectPr ?: return@forEachIndexed
            sectionElements += DocumentElement.Section(
                properties = sectionProperties.toSectionProperties(
                    sectionIndex = sectionIndex++,
                    source = "paragraph:$paragraphIndex"
                )
            )
        }

        document.document.body.sectPr?.let { bodySectPr ->
            sectionElements += DocumentElement.Section(
                properties = bodySectPr.toSectionProperties(
                    sectionIndex = sectionIndex,
                    source = "body"
                )
            )
        }

        return sectionElements
    }

    private fun parseHeadersAndFooters(document: XWPFDocument): List<DocumentElement.HeaderFooter> {
        val headers = document.headerList.mapIndexed { index, header ->
            DocumentElement.HeaderFooter(
                header.toContent(
                    kind = HeaderFooterKind.HEADER,
                    variant = resolveVariant(index, header.text)
                )
            )
        }
        val footers = document.footerList.mapIndexed { index, footer ->
            DocumentElement.HeaderFooter(
                footer.toContent(
                    kind = HeaderFooterKind.FOOTER,
                    variant = resolveVariant(index, footer.text)
                )
            )
        }
        return headers + footers
    }

    private fun parseFootnotes(document: XWPFDocument): List<DocumentElement.Note> =
        document.footnotes
            .orEmpty()
            .filterNot { it.id.toString() in setOf("-1", "0", "1") }
            .map { it.toNote(NoteKind.FOOTNOTE) }

    private fun parseEndnotes(document: XWPFDocument): List<DocumentElement.Note> =
        document.endnotes
            .orEmpty()
            .filterNot { it.id.toString() in setOf("-1", "0", "1") }
            .map { it.toNote(NoteKind.ENDNOTE) }

    private fun parseComments(document: XWPFDocument): List<DocumentElement.Comment> =
        document.comments
            .orEmpty()
            .map { comment ->
                DocumentElement.Comment(
                    CommentInfo(
                        id = comment.id,
                        author = comment.author,
                        initials = comment.initials,
                        date = comment.date?.time?.toInstant()?.toString(),
                        text = comment.text
                    )
                )
            }

    private fun CTSectPr.toSectionProperties(sectionIndex: Int, source: String): SectionProperties =
        SectionProperties(
            sectionIndex = sectionIndex,
            source = source,
            type = type?.`val`?.toString(),
            pageWidth = pgSz?.w.asInt(),
            pageHeight = pgSz?.h.asInt(),
            margins = EdgeInsets(
                top = pgMar?.top.asInt(),
                right = pgMar?.right.asInt(),
                bottom = pgMar?.bottom.asInt(),
                left = pgMar?.left.asInt()
            ),
            columnCount = cols?.num.asInt(),
            pageNumberStart = pgNumType?.start.asInt(),
            headerReferences = headerReferenceList.map {
                HeaderFooterReference(relationshipId = it.id, type = it.type?.toString())
            },
            footerReferences = footerReferenceList.map {
                HeaderFooterReference(relationshipId = it.id, type = it.type?.toString())
            }
        )

    private fun XWPFHeaderFooter.toContent(
        kind: HeaderFooterKind,
        variant: String
    ): HeaderFooterContent = HeaderFooterContent(
        kind = kind,
        variant = variant,
        text = text.orEmpty(),
        paragraphCount = paragraphs.size,
        tableCount = tables.size
    )

    private fun XWPFFootnote.toNote(kind: NoteKind): DocumentElement.Note =
        DocumentElement.Note(
            NoteInfo(
                kind = kind,
                id = id.toString(),
                text = paragraphs.joinToString("\n") { it.text }
            )
        )

    private fun XWPFEndnote.toNote(kind: NoteKind): DocumentElement.Note =
        DocumentElement.Note(
            NoteInfo(
                kind = kind,
                id = id.toString(),
                text = paragraphs.joinToString("\n") { it.text }
            )
        )

    private fun resolveVariant(index: Int, text: String): String {
        val lowered = text.lowercase()
        return when {
            index == 0 -> "primary"
            "first" in lowered -> "first"
            "even" in lowered -> "even"
            else -> "variant-$index"
        }
    }

    private fun Any?.asInt(): Int? = when (this) {
        is Number -> toInt()
        else -> toString().toIntOrNull()
    }
}
