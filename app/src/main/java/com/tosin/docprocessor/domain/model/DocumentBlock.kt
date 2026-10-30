package com.tosin.docprocessor.domain.model

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.LineSpacingRule
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.BookmarkBoundary
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.internal.models.DrawingInfo
import com.tosin.docprocessor.data.parser.internal.models.EmbeddedObjectInfo
import com.tosin.docprocessor.data.parser.internal.models.RichTableCell
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata

/**
 * Immutable block list model — the source of truth for the document.
 *
 * This replaces the parser-owned [DocumentElement] sealed class with an
 * immutable model that represents the same semantic content. The parser
 * continues to produce [DocumentElement]; a mapping layer converts
 * [DocumentElement] → [DocumentBlock] before pagination and rendering.
 *
 * Why immutable? When the user types a character, you only mutate the single
 * block they are editing rather than re-allocating memory for the entire
 * document. This makes undo/redo and text manipulation instantaneous.
 */
sealed interface DocumentBlock {
    val id: String

    // --- Paragraph ---

    data class Paragraph(
        override val id: String,
        val text: String,
        val style: TextStyleModel = TextStyleModel(),
        val spans: List<TextSpan> = emptyList(),
        val listInfo: ListInfo? = null,
        val hyperlink: HyperlinkInfo? = null,
        val listLabel: String? = null
    ) : DocumentBlock

    // --- Table ---

    data class Table(
        override val id: String,
        val rows: List<TableRowModel>,
        val hasHeader: Boolean = false,
        val metadata: TableMetadata = TableMetadata(),
        val richRows: List<List<RichTableCell>> = emptyList(),
        val gridColumnWidthsPt: List<Float> = emptyList()
    ) : DocumentBlock

    data class TableRow(
        override val id: String,
        val cells: List<TableCell>,
        val heightPoints: Double? = null,
        val isHeader: Boolean = false
    ) : DocumentBlock

    data class TableCell(
        override val id: String,
        val rich: RichTableCell = RichTableCell(
            spans = emptyList(),
            metadata = TableCellMetadata()
        ),
        val metadata: TableCellMetadata = TableCellMetadata()
    ) : DocumentBlock

    // --- ImageBlock ---

    data class ImageBlock(
        override val id: String,
        val sourceUri: String,
        val altText: String?,
        val caption: String? = null
    ) : DocumentBlock

    // --- SectionHeader ---

    data class SectionHeader(
        override val id: String,
        val text: String,
        val level: Int = 1,
        val style: TextStyleModel = TextStyleModel(),
        val headingFontSizePoints: Float? = null
    ) : DocumentBlock

    // --- Section ---

    data class Section(
        override val id: String,
        val properties: SectionProperties
    ) : DocumentBlock

    // --- PageBreak ---

    data class PageBreak(
        override val id: String
    ) : DocumentBlock
}

/**
 * Immutable text style model.
 *
 * Replaces [ParagraphStyle] with a simpler, data-oriented container that
 * is easier to reason about and to reuse across [DocumentBlock] types.
 */
data class TextStyleModel(
    val alignment: ParagraphAlignment = ParagraphAlignment.START,
    val indentation: ParagraphIndentation = ParagraphIndentation(),
    val spacing: ParagraphSpacing = ParagraphSpacing(),
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val color: String = "000000",
    val lineRule: LineSpacingRule? = null
)

/**
 * Immutable table row model.
 *
 * Replaces the parser-owned table row wrapper with a cleaner structure.
 */
data class TableRowModel(
    val cells: List<TableCell>,
    val heightPoints: Double? = null,
    val isHeader: Boolean = false
)

/**
 * Immutable table cell model carrying rich content and cell metadata.
 */
data class TableCell(
    val rich: RichTableCell = RichTableCell(
        spans = emptyList(),
        metadata = TableCellMetadata()
    ),
    val metadata: TableCellMetadata = TableCellMetadata()
)
