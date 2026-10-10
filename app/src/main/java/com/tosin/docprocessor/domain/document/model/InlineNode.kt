package com.tosin.docprocessor.domain.document.model

import com.tosin.docprocessor.domain.document.model.style.TextStyle

enum class BreakKind { LINE, PAGE, COLUMN, SOFT_PAGE, CARRIAGE, CACHED_PAGE }

enum class NoteKind { FOOTNOTE, ENDNOTE }

enum class RevisionKind { INSERTION, DELETION, MOVE_FROM, MOVE_TO, FORMAT_CHANGE }

/**
 * Inline content. Children of container nodes preserve source order exactly;
 * mappers never flatten runs.
 */
sealed interface InlineNode {
    val anchor: SourceAnchor?

    data class Text(
        val text: String,
        val style: TextStyle = TextStyle(),
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Span(
        val children: List<InlineNode>,
        val style: TextStyle = TextStyle(),
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Break(val kind: BreakKind, override val anchor: SourceAnchor? = null) : InlineNode

    data class Tab(override val anchor: SourceAnchor? = null) : InlineNode

    data class NoBreakHyphen(override val anchor: SourceAnchor? = null) : InlineNode

    data class SoftHyphen(override val anchor: SourceAnchor? = null) : InlineNode

    data class Symbol(
        val characterCode: String?,
        val font: String? = null,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Hyperlink(
        val children: List<InlineNode>,
        val target: String? = null,
        val anchorName: String? = null,
        val tooltip: String? = null,
        val docLocation: String? = null,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class BookmarkBoundary(
        val bookmarkId: String,
        val name: String? = null,
        val isStart: Boolean = true,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class NoteReference(
        val kind: NoteKind,
        val referenceId: String,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class CommentBoundary(
        val commentId: String,
        val isStart: Boolean = true,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class CommentReference(
        val commentId: String,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Field(
        val kind: String?,
        val instruction: String,
        val result: List<InlineNode> = emptyList(),
        val isSimple: Boolean = false,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Revision(
        val kind: RevisionKind,
        val author: String? = null,
        val date: String? = null,
        val children: List<InlineNode> = emptyList(),
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class ContentControl(
        val tag: String? = null,
        val alias: String? = null,
        val binding: String? = null,
        val children: List<InlineNode> = emptyList(),
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Ruby(
        val base: List<InlineNode>,
        val rubyText: List<InlineNode>,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Drawing(
        val frameStyle: com.tosin.docprocessor.domain.document.model.style.FrameStyle =
            com.tosin.docprocessor.domain.document.model.style.FrameStyle(),
        val resourceId: String? = null,
        val altText: String? = null,
        val title: String? = null,
        override val anchor: SourceAnchor? = null
    ) : InlineNode

    data class Opaque(val node: OpaqueNode) : InlineNode {
        override val anchor: SourceAnchor? get() = node.anchor
    }
}
