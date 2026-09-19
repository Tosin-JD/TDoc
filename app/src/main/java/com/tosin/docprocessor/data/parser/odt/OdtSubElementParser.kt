package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.BookmarkBoundary
import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteKind
import org.w3c.dom.Element

/**
 * Parses ODT structural and inline sub-elements that appear alongside the main
 * text flow: headers/footers, footnotes/endnotes, comments, bookmarks, and
 * fields. Returns null when [element] is not one of these, allowing the caller
 * to recurse.
 */
class OdtSubElementParser {

    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"

    fun tryParse(element: Element): List<DocumentElement>? = when (element.localName) {
        "header", "header-left", "header-right", "header-even",
        "footer", "footer-left", "footer-right", "footer-even" ->
            parseHeaderFooter(element)
        "note" -> parseNote(element)?.let { listOf(it) }
        "bookmark" -> listOf(parseBookmark(element, BookmarkBoundary.START))
        "bookmark-start" -> listOf(parseBookmark(element, BookmarkBoundary.START))
        "bookmark-end" -> listOf(parseBookmark(element, BookmarkBoundary.END))
        "page-number", "page-count", "page-continuation",
        "date", "time", "user-field-get", "user-field-input",
        "sequence", "sequence-ref", "reference-ref", "chapter",
        "file-name", "sender-phone-work", "page-variable-get",
        "variable-get", "variable-input", "variable-set" ->
            listOf(parseField(element))
        "annotation", "annotation-end" -> parseAnnotation(element)
        else -> null
    }

    // ------------------------------------------------------------------
    // Headers / footers
    // ------------------------------------------------------------------

    private fun parseHeaderFooter(element: Element): List<DocumentElement> {
        val kind = if (element.localName.startsWith("header")) {
            HeaderFooterKind.HEADER
        } else {
            HeaderFooterKind.FOOTER
        }
        val variant = element.localName.removePrefix(if (kind == HeaderFooterKind.HEADER) "header" else "footer")
            .ifEmpty { "default" }

        val paragraphElements = element.getElementsByTagNameNS(textNs, "p")

        return listOf(
            DocumentElement.HeaderFooter(
                HeaderFooterContent(
                    kind = kind,
                    variant = variant,
                    text = element.textContent.orEmpty().trim(),
                    paragraphCount = paragraphElements.length,
                    tableCount = element.getElementsByTagNameNS(tableNs, "table").length,
                    containsWatermark = element.getElementsByTagNameNS(drawNs, "frame").length > 0
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // Footnotes / endnotes
    // ------------------------------------------------------------------

    private fun parseNote(element: Element): DocumentElement.Note? {
        val noteClass = element.getAttributeNS(textNs, "note-class").ifEmpty { "footnote" }
        val body = element.getElementsByTagNameNS(textNs, "note-body").item(0) as? Element
        val text = body?.textContent?.trim() ?: element.textContent?.trim() ?: return null
        return DocumentElement.Note(
            NoteInfo(
                kind = if (noteClass == "endnote") NoteKind.ENDNOTE else NoteKind.FOOTNOTE,
                id = element.getAttributeNS(textNs, "id").ifEmpty { noteClass },
                text = text
            )
        )
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    private fun parseBookmark(element: Element, boundary: BookmarkBoundary): DocumentElement.Bookmark {
        val name = element.getAttributeNS(textNs, "name").ifEmpty {
            element.getAttributeNS(textNs, "id")
        }
        return DocumentElement.Bookmark(
            BookmarkInfo(
                id = name,
                name = name,
                boundary = boundary,
                source = "ODT"
            )
        )
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private fun parseField(element: Element): DocumentElement.Field {
        val type = element.localName?.takeIf { it.isNotBlank() } ?: "field"
        val textValue = element.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val name = element.getAttributeNS(textNs, "name").ifEmpty { null }
        val format = element.getAttributeNS(textNs, "display").ifEmpty {
            element.getAttributeNS(textNs, "time-style").ifEmpty { null }
        }
        return DocumentElement.Field(
            FieldInfo(
                type = type,
                instruction = name ?: format ?: type,
                value = textValue,
                isSimpleField = false,
                arguments = buildMap {
                    name?.let { put("name", it) }
                    format?.let { put("format", it) }
                },
                source = "ODT"
            )
        )
    }

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------

    private fun parseAnnotation(element: Element): List<DocumentElement>? {
        val id = element.getAttributeNS(officeNs, "name").ifEmpty {
            element.getAttributeNS(textNs, "id")
        }.ifEmpty { "annotation" }
        val text = element.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return listOf(
            DocumentElement.Comment(
                CommentInfo(id = id, author = null, text = text)
            )
        )
    }

    companion object {
        private const val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        private const val drawNs = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    }
}