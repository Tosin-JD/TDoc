package com.tosin.docprocessor.data.common.model

import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.DrawingInfo
import com.tosin.docprocessor.data.parser.internal.models.EmbeddedObjectInfo
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.data.parser.internal.models.TextSpan

sealed class DocumentElement {
    data class Paragraph(
        val spans: List<TextSpan>,
        val listLabel: String? = null,
        val style: ParagraphStyle = ParagraphStyle(),
        val hyperlink: HyperlinkInfo? = null,
        val listInfo: ListInfo? = null
    ) : DocumentElement()

    data class Table(
        val rows: List<List<String>>,
        val hasHeader: Boolean = false,
        val metadata: TableMetadata = TableMetadata()
    ) : DocumentElement()

    data class Image(
        val sourceUri: String, // Use URI/Path instead of Bitmap to save memory
        val altText: String?,
        val caption: String? = null
    ) : DocumentElement()

    // Grouping metadata elements
    data class SectionHeader(val text: String, val level: Int = 1) : DocumentElement()
    data class Section(val properties: SectionProperties) : DocumentElement()
    data class HeaderFooter(val content: HeaderFooterContent) : DocumentElement()
    data class Note(val info: NoteInfo) : DocumentElement()
    data class Comment(val info: CommentInfo) : DocumentElement()
    data class Bookmark(val info: BookmarkInfo) : DocumentElement()
    data class Field(val info: FieldInfo) : DocumentElement()
    data class Metadata(val info: MetadataInfo) : DocumentElement()
    data class Drawing(val info: DrawingInfo) : DocumentElement()
    data class EmbeddedObject(val info: EmbeddedObjectInfo) : DocumentElement()

    object PageBreak : DocumentElement()
}
