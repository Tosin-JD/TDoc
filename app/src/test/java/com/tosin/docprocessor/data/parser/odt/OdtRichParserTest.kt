package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.SourceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdtRichParserTest {

    private val ns = """
        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    """.trimIndent()

    private fun entries(): Map<String, ByteArray> = mapOf(
        "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(),
        "content.xml" to """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:p>Hello ODT</text:p>
                  <text:list>
                    <text:list-item><text:p>First item</text:p></text:list-item>
                    <text:list-item><text:p>Second item</text:p></text:list-item>
                  </text:list>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent().toByteArray(),
        "styles.xml" to "<office:document-styles $ns><office:styles/></office:document-styles>".toByteArray()
    )

    @Test
    fun `maps paragraphs and lists into the rich model`() {
        val parser = OdtDocumentParser(entries())

        val result = parser.parseEntries(entries())

        assertEquals(SourceFormat.ODT, result.document.sourceFormat)
        val paragraph = result.document.blocks.first() as BlockNode.Paragraph
        assertEquals("Hello ODT", text(paragraph.inlines))
        val list = result.document.blocks.filterIsInstance<BlockNode.ListBlock>().single()
        assertEquals(2, list.items.size)
        assertTrue(result.content.isNotEmpty())
    }

    private fun text(inlines: List<InlineNode>): String = buildString {
        inlines.forEach { inline ->
            when (inline) {
                is InlineNode.Text -> append(inline.text)
                is InlineNode.Span -> append(text(inline.children))
                else -> Unit
            }
        }
    }
}
