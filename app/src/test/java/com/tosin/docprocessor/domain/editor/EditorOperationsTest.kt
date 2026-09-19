package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.model.headingLevel
import com.tosin.docprocessor.model.isTextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorOperationsTest {

    private val meta = DocumentMeta(uri = "content://doc", fileName = "doc.txt", mimeType = "text/plain")
    private val textBlock = EditorBlock(id = 1L, spans = listOf(TextSpan(text = "Hello")))
    private val heading = EditorBlock(id = 2L, type = BlockType.HEADING1, spans = listOf(TextSpan(text = "Title")))
    private val table = EditorBlock(id = 3L, type = BlockType.TABLE)

private fun docOf(vararg blocks: EditorBlock) = EditorDocument(
        meta = meta,
        blocks = blocks.toList(),
        nextBlockId = (blocks.maxOfOrNull { it.id } ?: 0L) + 1
    )

    @Test
    fun `insertParagraphAfter inserts after target with fresh id`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.insertParagraphAfter(doc, textBlock.id)
        assertEquals(2, result.blocks.size)
        assertEquals(textBlock.id, result.blocks[0].id)
        assertTrue(result.blocks[1].type.isTextBlock)
        assertEquals(2L, result.blocks[1].id)
        assertEquals(3L, result.nextBlockId)
    }

    @Test
    fun `insertParagraphAfter on missing block appends`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.insertParagraphAfter(doc, 99L)
        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.last().type.isTextBlock)
    }

    @Test
    fun `deleteBlock removes the block`() {
        val doc = docOf(textBlock, heading)
        val result = EditorOperations.deleteBlock(doc, textBlock.id)
        assertEquals(listOf(heading.id), result.blocks.map { it.id })
    }

    @Test
    fun `deleteBlock of the last text block leaves an editable paragraph`() {
        val doc = docOf(table, textBlock)
        val result = EditorOperations.deleteBlock(doc, textBlock.id)
        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.first { it.type.isTextBlock }.spanCountsAsEditable())
        assertTrue(result.blocks.indexOfFirst { it.type.isTextBlock } > 0)
    }

    @Test
    fun `ensureAtLeastOneParagraph keeps an existing text block`() {
        val doc = docOf(textBlock)
        assertEquals(doc, EditorOperations.ensureAtLeastOneParagraph(doc))
    }

    @Test
    fun `ensureAtLeastOneParagraph appends a paragraph to an empty document`() {
        val doc = EditorDocument(meta = meta, blocks = listOf(table), nextBlockId = 1L)
        val result = EditorOperations.ensureAtLeastOneParagraph(doc)
        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.last().type.isTextBlock)
    }

    @Test
    fun `toggleFormat toggles selection`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.toggleFormat(doc, textBlock.id, Selection(0, 5), SpanProperty.BOLD)
        assertTrue(result.blockAt(textBlock.id)!!.spans.single().isBold)
    }

    @Test
    fun `applyFormat sets property value`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.applyFormat(doc, textBlock.id, Selection(0, 5), SpanProperty.UNDERLINE, true)
        assertTrue(result.blockAt(textBlock.id)!!.spans.single().isUnderline)
    }

    @Test
    fun `editSpans only changes the target block`() {
        val doc = docOf(textBlock, heading)
        val newSpans = listOf(TextSpan(text = "Changed"))
        val result = EditorOperations.editSpans(doc, textBlock.id, newSpans)
        assertEquals(newSpans, result.blockAt(textBlock.id)!!.spans)
        assertEquals(heading.spans, result.blockAt(heading.id)!!.spans)
    }

    @Test
    fun `setHeading updates type and style`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.setHeading(doc, textBlock.id, 2)
        val block = result.blockAt(textBlock.id)!!
        assertEquals(BlockType.HEADING2, block.type)
        assertEquals(2, block.type.headingLevel)
        assertEquals(2, block.style.headingLevel)
        assertTrue(block.style.isHeading)

        val cleared = EditorOperations.setHeading(result, textBlock.id, null)
        val plain = cleared.blockAt(textBlock.id)!!
        assertEquals(BlockType.PARAGRAPH, plain.type)
        assertFalse(plain.style.isHeading)
        assertNull(plain.style.headingLevel)
    }

    @Test
    fun `setAlignment updates alignment`() {
        val doc = docOf(textBlock)
        val result = EditorOperations.setAlignment(doc, textBlock.id, ParagraphAlignment.CENTER)
        assertEquals(ParagraphAlignment.CENTER, result.blockAt(textBlock.id)!!.style.alignment)
    }

    @Test
    fun `toggleBulletList adds then removes label and info`() {
        val doc = docOf(textBlock)
        val listed = EditorOperations.toggleBulletList(doc, textBlock.id)
        val block = listed.blockAt(textBlock.id)!!
        assertEquals("• ", block.listLabel)
        assertEquals(ListInfo(level = 0, format = "bullet"), block.listInfo)

        val cleared = EditorOperations.toggleBulletList(listed, textBlock.id)
        val restored = cleared.blockAt(textBlock.id)!!
        assertNull(restored.listLabel)
        assertNull(restored.listInfo)
    }

    @Test
    fun `blockAfter returns following block or null for last`() {
        val doc = docOf(textBlock, heading, table)
        assertEquals(heading, EditorOperations.blockAfter(doc, textBlock.id))
        assertNull(EditorOperations.blockAfter(doc, table.id))
        assertNull(EditorOperations.blockAfter(doc, 999L))
    }

    @Test
    fun `updateBlock is a no-op for missing block`() {
        val doc = docOf(textBlock)
        assertEquals(doc, EditorOperations.updateBlock(doc, 404L) { it })
    }

    private fun EditorBlock.spanCountsAsEditable(): Boolean = type.isTextBlock
}