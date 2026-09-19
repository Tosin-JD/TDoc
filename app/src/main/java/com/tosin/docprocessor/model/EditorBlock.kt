package com.tosin.docprocessor.model

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan

/**
 * Block type mirrors document element kinds that are directly editable (text)
 * vs read-only placeholders that keep their position in the flow.
 */
enum class BlockType {
    PARAGRAPH,
    HEADING1, HEADING2, HEADING3, HEADING4, HEADING5, HEADING6,
    TABLE,
    IMAGE,
    METADATA,
    PAGE_BREAK;

    companion object {
        fun fromOutlineLevel(level: Int?): BlockType? = when (level) {
            null -> null
            1 -> HEADING1
            2 -> HEADING2
            3 -> HEADING3
            4 -> HEADING4
            5 -> HEADING5
            else -> HEADING6
        }
    }
}

val BlockType.isTextBlock: Boolean
    get() = this == BlockType.PARAGRAPH ||
        headingLevel != null

val BlockType.headingLevel: Int?
    get() = when (this) {
        BlockType.HEADING1 -> 1
        BlockType.HEADING2 -> 2
        BlockType.HEADING3 -> 3
        BlockType.HEADING4 -> 4
        BlockType.HEADING5 -> 5
        BlockType.HEADING6 -> 6
        else -> null
    }

/**
 * An editable unit in the document model. Text blocks reference spans in the
 * parser model ([TextSpan]); non-text blocks keep their original
 * [DocumentElement] for lossless round-trip on save.
 */
data class EditorBlock(
    val id: Long,
    val type: BlockType = BlockType.PARAGRAPH,
    val spans: List<TextSpan> = emptyList(),
    val style: ParagraphStyle = ParagraphStyle(),
    val listLabel: String? = null,
    val listInfo: ListInfo? = null,
    val element: DocumentElement? = null
) {
    val text: String
        get() = spans.joinToString("") { it.text }

    companion object {
        fun paragraph(id: Long): EditorBlock =
            EditorBlock(id = id, type = BlockType.PARAGRAPH, spans = emptyList())

        fun heading(id: Long, level: Int): EditorBlock =
            EditorBlock(
                id = id,
                type = BlockType.fromOutlineLevel(level) ?: BlockType.HEADING1,
                spans = emptyList()
            )
    }
}