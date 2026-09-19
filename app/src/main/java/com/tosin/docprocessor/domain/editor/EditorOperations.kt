package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.model.isTextBlock

/**
 * Pure functions that turn a document edit into a new [EditorDocument].
 * Everything lives in immutable copies so history snapshots stay valid.
 */
object EditorOperations {

    fun updateBlock(
        doc: EditorDocument,
        blockId: Long,
        transform: (EditorBlock) -> EditorBlock
    ): EditorDocument {
        val block = doc.blockAt(blockId) ?: return doc
        return doc.replaceBlock(transform(block))
    }

    fun editSpans(doc: EditorDocument, blockId: Long, spans: List<TextSpan>): EditorDocument =
        updateBlock(doc, blockId) { it.copy(spans = spans) }

    fun toggleFormat(
        doc: EditorDocument,
        blockId: Long,
        selection: Selection,
        property: SpanProperty
    ): EditorDocument = updateBlock(doc, blockId) { block ->
        block.copy(spans = SpanOps.toggle(block.spans, selection, property))
    }

    fun applyFormat(
        doc: EditorDocument,
        blockId: Long,
        selection: Selection,
        property: SpanProperty,
        value: Boolean
    ): EditorDocument = updateBlock(doc, blockId) { block ->
        block.copy(spans = SpanOps.applyRange(block.spans, selection) { SpanOps.applyProperty(it, property, value) })
    }

    fun setHeading(doc: EditorDocument, blockId: Long, level: Int?): EditorDocument =
        updateBlock(doc, blockId) { block ->
            val type = if (level == null) BlockType.PARAGRAPH
            else BlockType.fromOutlineLevel(level) ?: BlockType.HEADING6
            block.copy(
                type = type,
                style = block.style.copy(isHeading = level != null, headingLevel = level, outlineLevel = level)
            )
        }

    fun setAlignment(
        doc: EditorDocument,
        blockId: Long,
        alignment: ParagraphAlignment
    ): EditorDocument = updateBlock(doc, blockId) { block ->
        block.copy(style = block.style.copy(alignment = alignment))
    }

    fun toggleBulletList(doc: EditorDocument, blockId: Long): EditorDocument =
        updateBlock(doc, blockId) { block ->
            if (block.listLabel == null) {
                block.copy(listLabel = "• ", listInfo = ListInfo(level = 0, format = "bullet"))
            } else {
                block.copy(listLabel = null, listInfo = null)
            }
        }

    fun insertParagraphAfter(doc: EditorDocument, blockId: Long): EditorDocument {
        val (id, withId) = doc.allocateBlockId()
        return withId.insertAfter(blockId, EditorBlock.paragraph(id))
    }

    fun deleteBlock(doc: EditorDocument, blockId: Long): EditorDocument {
        var result = doc.removeBlock(blockId)
        val hasTextBlock = result.blocks.any { it.type.isTextBlock }
        if (!hasTextBlock) {
            val (id, withId) = result.allocateBlockId()
            result = withId.append(EditorBlock.paragraph(id))
        }
        return result
    }

    /**
     * Guarantees the document contains at least one editable paragraph so an
     * empty (newly created) document can always receive text.
     */
    fun ensureAtLeastOneParagraph(doc: EditorDocument): EditorDocument {
        if (doc.blocks.any { it.type.isTextBlock }) return doc
        val (id, withId) = doc.allocateBlockId()
        return withId.append(EditorBlock.paragraph(id))
    }

    /** The block that follows a given block; returns null when it is the last block. */
    fun blockAfter(doc: EditorDocument, blockId: Long): EditorBlock? {
        val index = doc.indexOf(blockId)
        if (index < 0) return null
        return doc.blocks.getOrNull(index + 1)
    }
}