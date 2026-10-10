package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxRichParserTest {

    private fun entries(): Map<String, ByteArray> = mapOf(
        "[Content_Types].xml" to """
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent().toByteArray(),
        "_rels/.rels" to """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(),
        "word/document.xml" to """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Hello DOCX</w:t></w:r></w:p>
                <w:p><w:r><w:t xml:space="preserve">Second </w:t></w:r><w:r><w:t>paragraph</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray()
    )

    @Test
    fun `maps paragraphs into the rich model and the legacy projection`() {
        val parser = DocxDocumentParser(entries())

        val result = parser.parseEntries(entries())

        assertEquals(SourceFormat.DOCX, result.document.sourceFormat)
        val paragraphs = result.document.blocks.filterIsInstance<BlockNode.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("Hello DOCX", text(paragraphs[0].inlines))
        assertEquals("Second paragraph", text(paragraphs[1].inlines))
        assertTrue(result.content.any { it is DocumentElement.Paragraph })
    }

    @Test
    fun `flags a package without content types instead of dropping it silently`() {
        val minimal = mapOf("word/document.xml" to entries().getValue("word/document.xml"))
        val parser = DocxDocumentParser(minimal)

        val result = parser.parseEntries(minimal)

        assertTrue(result.diagnostics.any { it.construct == "package" })
    }

    @Test
    fun `preserves unknown block constructs as a placeholder element`() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:x="urn:example:weird">
              <w:body>
                <w:p><w:r><w:t>Intro</w:t></w:r></w:p>
                <x:customThing><x:payload>data</x:payload></x:customThing>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray()
        val minimal = mapOf("word/document.xml" to xml)

        val result = DocxDocumentParser(minimal).parseEntries(minimal)

        assertTrue(result.content.any { it is DocumentElement.EmbeddedObject })
        assertTrue(result.diagnostics.any { it.severity == ParseDiagnostic.Severity.WARNING })
    }

    private fun text(inlines: List<InlineNode>): String = buildString {
        inlines.forEach { inline ->
            when (inline) {
                is InlineNode.Text -> append(inline.text)
                is InlineNode.Span -> append(text(inline.children))
                is InlineNode.Hyperlink -> append(text(inline.children))
                else -> Unit
            }
        }
    }
}
