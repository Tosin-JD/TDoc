package com.tosin.docprocessor.domain.model

import com.tosin.docprocessor.data.parser.internal.models.*
import java.util.UUID

sealed class DocumentElement {
    abstract val id: String

    data class Paragraph(
        override val id: String = UUID.randomUUID().toString(),
        val spans: List<TextSpan>,
        val listLabel: String? = null,
        val style: ParagraphStyle = ParagraphStyle(),
        val hyperlink: HyperlinkInfo? = null,
        val listInfo: ListInfo? = null
    ) : DocumentElement()

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        val rows: List<List<String>>,
        val hasHeader: Boolean = false,
        val metadata: TableMetadata = TableMetadata(),
        /** Rich per-cell content carrying styled spans. Populated by [LegacyElementAdapter]. */
        val richRows: List<List<com.tosin.docprocessor.data.parser.internal.models.RichTableCell>> = emptyList(),
        /** Parsed column grid widths in points (from `w:tblGrid`). Empty = use heuristic. */
        val gridColumnWidthsPt: List<Float> = emptyList()
    ) : DocumentElement()


    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val sourceUri: String,
        val altText: String?,
        val caption: String? = null
    ) : DocumentElement()

    data class SectionHeader(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val level: Int = 1,
        val style: ParagraphStyle = ParagraphStyle()
    ) : DocumentElement()

    data class Section(
        override val id: String = UUID.randomUUID().toString(),
        val properties: SectionProperties
    ) : DocumentElement()

    data class HeaderFooter(
        override val id: String = UUID.randomUUID().toString(),
        val content: HeaderFooterContent
    ) : DocumentElement()

    data class Note(
        override val id: String = UUID.randomUUID().toString(),
        val info: NoteInfo
    ) : DocumentElement()

    data class Comment(
        override val id: String = UUID.randomUUID().toString(),
        val info: CommentInfo
    ) : DocumentElement()

    data class Bookmark(
        override val id: String = UUID.randomUUID().toString(),
        val info: BookmarkInfo
    ) : DocumentElement()

    data class Field(
        override val id: String = UUID.randomUUID().toString(),
        val info: FieldInfo
    ) : DocumentElement()

    data class Metadata(
        override val id: String = UUID.randomUUID().toString(),
        val info: MetadataInfo
    ) : DocumentElement()

    data class Drawing(
        override val id: String = UUID.randomUUID().toString(),
        val info: DrawingInfo
    ) : DocumentElement()

    data class EmbeddedObject(
        override val id: String = UUID.randomUUID().toString(),
        val info: EmbeddedObjectInfo
    ) : DocumentElement()

    data class PageBreak(
        override val id: String = UUID.randomUUID().toString()
    ) : DocumentElement()
}
