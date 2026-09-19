package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.model.headingLevel

/**
 * Bridges the parser element list and the editable document model.
 * Round-tripping [EditorDocument] back to elements is lossless: non-text
 * blocks keep their original [DocumentElement] untouched.
 */
object EditorTransformer {

    fun toEditorDocument(
        elements: List<DocumentElement>,
        meta: DocumentMeta
    ): EditorDocument {
        var nextId = 1L
        val blocks = elements.map { element ->
            val id = nextId++
            when (element) {
                is DocumentElement.Paragraph -> {
                    val level = element.style.headingLevel
                    when {
                        level != null -> EditorBlock(
                            id = id,
                            type = BlockType.fromOutlineLevel(level) ?: BlockType.HEADING6,
                            spans = element.spans,
                            style = element.style,
                            listLabel = element.listLabel,
                            listInfo = element.listInfo
                        )
                        else -> EditorBlock(
                            id = id,
                            type = BlockType.PARAGRAPH,
                            spans = element.spans,
                            style = element.style,
                            listLabel = element.listLabel,
                            listInfo = element.listInfo
                        )
                    }
                }
                is DocumentElement.SectionHeader -> EditorBlock(
                    id = id,
                    type = BlockType.fromOutlineLevel(element.level) ?: BlockType.HEADING1,
                    spans = listOf(TextSpan(text = element.text, color = "000000"))
                )
                is DocumentElement.Table -> EditorBlock(id = id, type = BlockType.TABLE, element = element)
                is DocumentElement.Image -> EditorBlock(id = id, type = BlockType.IMAGE, element = element)
                DocumentElement.PageBreak -> EditorBlock(id = id, type = BlockType.PAGE_BREAK, element = element)
                else -> EditorBlock(id = id, type = BlockType.METADATA, element = element)
            }
        }
        return EditorDocument(meta = meta, blocks = blocks, nextBlockId = nextId)
    }

    fun toElements(doc: EditorDocument): List<DocumentElement> =
        doc.blocks.map { block ->
            when (block.type) {
                BlockType.PARAGRAPH -> DocumentElement.Paragraph(
                    spans = block.spans,
                    listLabel = block.listLabel,
                    style = block.style,
                    listInfo = block.listInfo
                )
                BlockType.HEADING1, BlockType.HEADING2, BlockType.HEADING3,
                BlockType.HEADING4, BlockType.HEADING5, BlockType.HEADING6 ->
                    DocumentElement.SectionHeader(text = block.text, level = block.type.headingLevel ?: 1)
                else -> block.element ?: DocumentElement.Paragraph(
                    spans = block.spans,
                    listLabel = block.listLabel,
                    style = block.style,
                    listInfo = block.listInfo
                )
            }
        }
}