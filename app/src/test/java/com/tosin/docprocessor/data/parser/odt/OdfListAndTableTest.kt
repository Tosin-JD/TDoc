package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.HeaderFooterKind
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.style.ListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdfListAndTableTest {

    private val ns = """
        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
        xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    """.trimIndent()

    private fun entries(contentXml: String): Map<String, ByteArray> = mapOf(
        "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(),
        "content.xml" to contentXml.toByteArray(),
        "styles.xml" to "<office:document-styles $ns><office:styles/></office:document-styles>".toByteArray()
    )

    @Test
    fun `ordered list markers use the level format instead of the raw template`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <text:list-style style:name="L1">
                  <text:list-level-style-number text:level="1" text:num-format="a"
                      text:start-value="1" style:num-prefix="" style:num-suffix="."/>
                </text:list-style>
              </office:automatic-styles>
              <office:body>
                <office:text>
                  <text:list text:style-name="L1">
                    <text:list-item><text:p>alpha one</text:p></text:list-item>
                    <text:list-item><text:p>alpha two</text:p></text:list-item>
                    <text:list-item><text:p>alpha three</text:p></text:list-item>
                  </text:list>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = OdtDocumentParser(entries(content)).parseEntries(entries(content))

        val list = result.document.blocks.single() as BlockNode.ListBlock
        assertEquals(ListKind.ORDERED, list.kind)
        assertEquals("a.", list.items[0].marker?.text)
        assertEquals("b.", list.items[1].marker?.text)
        assertEquals("c.", list.items[2].marker?.text)
    }

    @Test
    fun `table grid columns repeated rows and row heights are parsed`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <style:style style:name="co1" style:family="table-column">
                  <style:table-column-properties style:column-width="1in"/>
                </style:style>
                <style:style style:name="ro1" style:family="table-row">
                  <style:row-properties style:row-height="0.5in"/>
                </style:style>
              </office:automatic-styles>
              <office:body>
                <office:text>
                  <table:table table:name="T1">
                    <table:table-column table:style-name="co1" table:number-columns-repeated="3"/>
                    <table:table-row table:style-name="ro1" table:number-rows-repeated="2">
                      <table:table-cell><text:p>r1c1</text:p></table:table-cell>
                      <table:table-cell><text:p>r1c2</text:p></table:table-cell>
                      <table:table-cell><text:p>r1c3</text:p></table:table-cell>
                    </table:table-row>
                  </table:table>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = OdtDocumentParser(entries(content)).parseEntries(entries(content))

        val table = result.document.blocks.single() as BlockNode.Table
        assertEquals(3, table.gridColumns.size)
        assertTrue(table.gridColumns.all { it != null })
        assertEquals(72.0, table.gridColumns[0]!!, 0.01)
        assertEquals(2, table.rows.size)
        assertEquals(0.5 * 72.0, table.rows[0].heightPoints!!, 0.01)
        assertEquals(3, table.rows[0].cells.size)
        // number-rows-repeated duplicates the row including its content.
        assertEquals(3, table.rows[1].cells.size)
        assertEquals(0.5 * 72.0, table.rows[1].heightPoints!!, 0.01)
    }

    @Test
    fun `odf text fields become field nodes instead of being dropped`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:p>Page <text:page-number>3</text:page-number> of <text:page-count>12</text:page-count></text:p>
                  <text:p><text:ref text:ref-name="sec1">Introduction</text:ref></text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = OdtDocumentParser(entries(content)).parseEntries(entries(content))

        val paragraph = result.document.blocks.first() as BlockNode.Paragraph
        val fields = paragraph.inlines.filterIsInstance<InlineNode.Field>()
        assertEquals(2, fields.size)
        assertEquals("PAGE", fields[0].kind)
        assertEquals("NUMPAGES", fields[1].kind)
        assertEquals("text:page-number", fields[0].instruction)
        val refParagraph = result.document.blocks[1] as BlockNode.Paragraph
        val ref = refParagraph.inlines.filterIsInstance<InlineNode.Field>().single()
        assertEquals("REF", ref.kind)
        assertTrue(ref.instruction.contains("sec1"))
        assertNotNull(ref.result.firstOrNull())
    }

    @Test
    fun `ordered markers support upper roman and never emit the raw template`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <text:list-style style:name="L2">
                  <text:list-level-style-number text:level="1" text:num-format="I"
                      text:start-value="1" style:num-prefix="" style:num-suffix="."/>
                </text:list-style>
              </office:automatic-styles>
              <office:body>
                <office:text>
                  <text:list text:style-name="L2">
                    <text:list-item><text:p>roman one</text:p></text:list-item>
                    <text:list-item><text:p>roman two</text:p></text:list-item>
                    <text:list-item><text:p>roman three</text:p></text:list-item>
                  </text:list>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = OdtDocumentParser(entries(content)).parseEntries(entries(content))

        val list = result.document.blocks.single() as BlockNode.ListBlock
        assertEquals("I.", list.items[0].marker?.text)
        assertEquals("II.", list.items[1].marker?.text)
        assertEquals("III.", list.items[2].marker?.text)
        assertFalse(list.items.any { it.marker?.text?.contains("%1") == true })
    }

    @Test
    fun `header story keeps page number fields`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text><text:p>Body</text:p></office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()
        val styles = """
            <office:document-styles $ns>
              <office:master-styles>
                <style:master-page style:name="Standard">
                  <style:header>
                    <text:p>Page <text:page-number>3</text:page-number> of <text:page-count>12</text:page-count></text:p>
                  </style:header>
                </style:master-page>
              </office:master-styles>
            </office:document-styles>
        """.trimIndent()
        val entries = mapOf(
            "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(),
            "content.xml" to content.toByteArray(),
            "styles.xml" to styles.toByteArray()
        )

        val result = OdtDocumentParser(entries).parseEntries(entries)

        val header = result.document.headerFooters.single()
        assertEquals(HeaderFooterKind.HEADER, header.kind)
        val paragraph = header.blocks.single() as BlockNode.Paragraph
        val fields = paragraph.inlines.filterIsInstance<InlineNode.Field>()
        assertEquals(2, fields.size)
        assertEquals("PAGE", fields[0].kind)
        assertEquals("NUMPAGES", fields[1].kind)
        assertEquals("3", fields[0].result.filterIsInstance<InlineNode.Text>().single().text.trim())
    }
}
