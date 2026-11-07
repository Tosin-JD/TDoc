package com.tosin.docprocessor.data.writer.common

import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.DocumentMetadata
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDocumentExportAdapterTest {
    @Test
    fun convertsEditorBlocksAndRetainsRichDocumentMetadata() {
        val source = ParsedDocument(metadata = DocumentMetadata(title = "Retained"), blocks = listOf(BlockNode.PageBreak()))
        val exported = LegacyDocumentExportAdapter().adapt(
            listOf(
                DocumentElement.Paragraph(spans = listOf(TextSpan("edited", isBold = true))),
                DocumentElement.SectionHeader(text = "Title", level = 2),
                DocumentElement.Table(rows = listOf(listOf("cell"))),
                DocumentElement.PageBreak()
            ),
            source
        )

        assertEquals("Retained", exported.metadata.title)
        assertEquals(4, exported.blocks.size)
        assertTrue(exported.blocks[0] is BlockNode.Paragraph)
        assertTrue(exported.blocks[1] is BlockNode.Heading)
        assertTrue(exported.blocks[2] is BlockNode.Table)
        assertTrue(exported.blocks[3] is BlockNode.PageBreak)
        val paragraph = exported.blocks.first() as BlockNode.Paragraph
        assertEquals(true, (paragraph.inlines.first() as com.tosin.docprocessor.domain.document.model.InlineNode.Text).style.bold)
    }
}
