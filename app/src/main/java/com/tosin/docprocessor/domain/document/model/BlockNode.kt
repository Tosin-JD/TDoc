package com.tosin.docprocessor.domain.document.model

import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.FrameStyle
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle

enum class IndexKind {
    TABLE_OF_CONTENTS, ALPHABETICAL, ILLUSTRATION, TABLE_INDEX, OBJECT, USER, BIBLIOGRAPHY
}

data class ListMarker(
    val text: String?,
    val level: Int,
    val ordered: Boolean,
    /** Numbering identity (DOCX `w:numId`, ODF list style name); null when unknown. */
    val numId: String? = null
)

/** Block-level content. Order is significant and preserved. */
sealed interface BlockNode {
    val anchor: SourceAnchor?

    data class Paragraph(
        val inlines: List<InlineNode>,
        val style: ParagraphStyle = ParagraphStyle(),
        val listMarker: ListMarker? = null,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class Heading(
        val inlines: List<InlineNode>,
        val level: Int,
        val style: ParagraphStyle = ParagraphStyle(),
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class ListBlock(
        val kind: ListKind,
        val style: ListStyle = ListStyle(),
        val items: List<ListItem>,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class ListItem(
        val blocks: List<BlockNode>,
        val marker: ListMarker? = null,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class Table(
        val style: TableStyle = TableStyle(),
        val gridColumns: List<Double?> = emptyList(),
        val rows: List<TableRow>,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class TableRow(
        val cells: List<TableCell>,
        val isHeader: Boolean = false,
        val heightPoints: Double? = null,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class TableCell(
        val blocks: List<BlockNode>,
        val style: CellStyle = CellStyle(),
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class Figure(
        val frameStyle: FrameStyle = FrameStyle(),
        val resourceId: String? = null,
        val caption: List<InlineNode> = emptyList(),
        val altText: String? = null,
        val title: String? = null,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class TextBox(
        val blocks: List<BlockNode>,
        val frameStyle: FrameStyle = FrameStyle(),
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class SectionBreak(
        val style: SectionStyle,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class PageBreak(override val anchor: SourceAnchor? = null) : BlockNode

    data class IndexBlock(
        val kind: IndexKind,
        val title: String? = null,
        val entries: List<String> = emptyList(),
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class NoteBody(
        val kind: NoteKind,
        val noteId: String,
        val blocks: List<BlockNode>,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class RevisionBlock(
        val kind: RevisionKind,
        val author: String? = null,
        val date: String? = null,
        val blocks: List<BlockNode>,
        override val anchor: SourceAnchor? = null
    ) : BlockNode

    data class Opaque(val node: OpaqueNode) : BlockNode {
        override val anchor: SourceAnchor? get() = node.anchor
    }
}
