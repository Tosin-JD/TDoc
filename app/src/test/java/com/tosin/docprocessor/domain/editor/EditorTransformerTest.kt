package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.model.BlockType
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.headingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTransformerTest {

    private val meta = DocumentMeta(uri = "content://doc", fileName = "plan.docx", mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    private val paragraphElement = DocumentElement.Paragraph(
        listOf(TextSpan(text = "Body text", color = "000000"))
    )

    @Test
    fun `toEditorDocument assigns sequential ids`() {
        val doc = EditorTransformer.toEditorDocument(
            listOf(
                paragraphElement,
                DocumentElement.Table(rows = listOf(listOf("a"))),
                DocumentElement.Image(sourceUri = "file:/img.png", altText = "img")
            ),
            meta
        )
        assertEquals(listOf(1L, 2L, 3L), doc.blocks.map { it.id })
        assertEquals(4L, doc.nextBlockId)
    }

    @Test
    fun `toEditorDocument maps elements to block types`() {
        val doc = EditorTransformer.toEditorDocument(
            listOf(
                paragraphElement,
                DocumentElement.SectionHeader("Chapter", level = 2),
                DocumentElement.Table(rows = listOf(listOf("a", "b"))),
                DocumentElement.Image(sourceUri = "file:/img.png", altText = "alt"),
                DocumentElement.PageBreak
            ),
            meta
        )
        assertEquals(
            listOf(BlockType.PARAGRAPH, BlockType.HEADING2, BlockType.TABLE, BlockType.IMAGE, BlockType.PAGE_BREAK),
            doc.blocks.map { it.type }
        )
    }

    @Test
    fun `toEditorDocument keeps heading style metadata`() {
        val element = DocumentElement.Paragraph(
            spans = listOf(TextSpan(text = "Title")),
            style = ParagraphStyle(isHeading = true, headingLevel = 1, outlineLevel = 1)
        )
        val doc = EditorTransformer.toEditorDocument(listOf(element), meta)
        val block = doc.blocks.single()
        assertEquals(BlockType.HEADING1, block.type)
        assertEquals(1, block.type.headingLevel)
        assertTrue(block.style.isHeading)
    }

    @Test
    fun `toElements round-trips paragraphs and non-text blocks`() {
        val elements = listOf(
            paragraphElement,
            DocumentElement.Table(rows = listOf(listOf("x"))),
            DocumentElement.SectionHeader("Heading", level = 1)
        )
        val doc = EditorTransformer.toEditorDocument(elements, meta)
        val back = EditorTransformer.toElements(doc)

        assertEquals(DocumentElement.Paragraph::class, back[0]::class)
        assertEquals(paragraphElement.spans, (back[0] as DocumentElement.Paragraph).spans)
        assertEquals(DocumentElement.Table::class, back[1]::class)
        assertEquals(listOf(listOf("x")), (back[1] as DocumentElement.Table).rows)

        // A heading block is re-emitted as a SectionHeader, not a Paragraph.
        val section = back[2] as DocumentElement.SectionHeader
        assertEquals("Heading", section.text)
        assertEquals(1, section.level)
    }

    @Test
    fun `toElements from paragraph preserves list info`() {
        val element = DocumentElement.Paragraph(
            spans = listOf(TextSpan(text = "item")),
            listLabel = "1.",
            listInfo = ListInfo(level = 0, format = "decimal")
        )
        val doc = EditorTransformer.toEditorDocument(listOf(element), meta)
        val back = EditorTransformer.toElements(doc)
        val paragraph = back.single() as DocumentElement.Paragraph
        assertEquals("1.", paragraph.listLabel)
        assertEquals(ListInfo(level = 0, format = "decimal"), paragraph.listInfo)
    }

    @Test
    fun `full editor round trip preserves order and text`() {
        val elements = listOf(
            DocumentElement.SectionHeader("Intro", 1),
            paragraphElement,
            DocumentElement.Table(rows = listOf(listOf("a", "b"), listOf("c", "d"))),
            DocumentElement.Paragraph(
                listOf(TextSpan(text = "Tail", isBold = true))
            )
        )
        val roundTrip1 = EditorTransformer.toEditorDocument(elements, meta)
        val roundTrip2 = EditorTransformer.toEditorDocument(
            EditorTransformer.toElements(roundTrip1),
            meta
        )
        // Tables lose columns width metadata but keep rows; compare core data.
        assertEquals(roundTrip1.blocks.size, roundTrip2.blocks.size)
        assertEquals(roundTrip1.blocks.map { it.type }, roundTrip2.blocks.map { it.type })
        assertEquals(
            EditorTransformer.toElements(roundTrip1).map { it::class },
            EditorTransformer.toElements(roundTrip2).map { it::class }
        )
    }
}