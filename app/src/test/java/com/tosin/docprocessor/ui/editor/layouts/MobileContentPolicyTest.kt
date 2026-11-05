package com.tosin.docprocessor.ui.editor.layouts

import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteKind
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileContentPolicyTest {

    private val section = DocumentElement.Section(
        properties = SectionProperties(
            sectionIndex = 0,
            source = "word/document.xml",
            pageWidth = 12240,
            pageHeight = 15840,
            margins = EdgeInsets(top = 1440, right = 1440, bottom = 1440, left = 1440),
            orientation = "portrait",
            columnCount = 1
        )
    )

    private fun documentElements(): List<DocumentElement> = listOf(
        section,
        DocumentElement.SectionHeader(text = "Introduction", level = 1),
        DocumentElement.Paragraph(spans = listOf(TextSpan(text = "Body text."))),
        DocumentElement.Table(rows = listOf(listOf("A", "B"))),
        DocumentElement.Image(sourceUri = "content://image/1", altText = "alt"),
        DocumentElement.PageBreak(),
        DocumentElement.HeaderFooter(
            content = HeaderFooterContent(
                kind = HeaderFooterKind.HEADER,
                variant = "default",
                text = "Chapter one",
                paragraphCount = 1,
                tableCount = 0
            )
        ),
        DocumentElement.Metadata(
            info = MetadataInfo(kind = "DOCX Metadata", summary = "Document processed via TDoc")
        ),
        DocumentElement.Field(
            info = FieldInfo(type = "PAGE", instruction = "PAGE", value = "1")
        ),
        DocumentElement.Bookmark(
            info = com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo(
                id = "1",
                name = "intro"
            )
        ),
        DocumentElement.Comment(
            info = CommentInfo(id = "1", text = "Check this")
        ),
        DocumentElement.Note(
            info = NoteInfo(kind = NoteKind.FOOTNOTE, id = "1", text = "A note")
        )
    )

    @Test
    fun `section setup is never body content`() {
        assertFalse(MobileContentPolicy.isBodyContent(section))
        assertNull(MobileContentPolicy.visibleText(section))
    }

    @Test
    fun `a document with a section marker exposes no internal serialization`() {
        val visible = documentElements().filter { MobileContentPolicy.isBodyContent(it) }
        val text = visible
            .mapNotNull { MobileContentPolicy.visibleText(it) }
            .joinToString(" ")

        assertFalse(text.contains("SectionProperties"))
        assertFalse(text.contains("twips"))
        assertFalse(text.contains("word/document.xml"))
        assertFalse(text.contains("EdgeInsets"))
        assertFalse(text.contains("HeaderFooter"))
        assertEquals(
            listOf(
                DocumentElement.SectionHeader::class,
                DocumentElement.Paragraph::class,
                DocumentElement.Table::class,
                DocumentElement.Image::class
            ),
            visible.map { it::class }
        )
    }

    @Test
    fun `control data and metadata records are excluded`() {
        val elements = documentElements()

        assertFalse(MobileContentPolicy.isBodyContent(elements[5])) // PageBreak
        assertFalse(MobileContentPolicy.isBodyContent(elements[6])) // HeaderFooter
        assertFalse(MobileContentPolicy.isBodyContent(elements[7])) // Metadata
        assertFalse(MobileContentPolicy.isBodyContent(elements[8])) // Field
        assertFalse(MobileContentPolicy.isBodyContent(elements[9])) // Bookmark
        assertFalse(MobileContentPolicy.isBodyContent(elements[10])) // Comment
        assertFalse(MobileContentPolicy.isBodyContent(elements[11])) // Note
    }

    @Test
    fun `empty visible artifacts are excluded`() {
        assertFalse(MobileContentPolicy.isBodyContent(DocumentElement.SectionHeader(text = "   ")))
        assertFalse(MobileContentPolicy.isBodyContent(DocumentElement.Table(rows = emptyList())))
    }

    @Test
    fun `body elements keep their order and text`() {
        val visible = documentElements().filter { MobileContentPolicy.isBodyContent(it) }

        assertTrue(visible.first() is DocumentElement.SectionHeader)
        assertTrue(visible[1] is DocumentElement.Paragraph)
        assertEquals("Body text.", MobileContentPolicy.visibleText(visible[1]))
        assertEquals("A\tB", MobileContentPolicy.visibleText(visible[2]))
    }
}
