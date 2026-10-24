package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdfRichExtensionsTest {

    private val ns = """
        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
        xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
        xmlns:xlink="http://www.w3.org/1999/xlink"
        xmlns:dc="http://purl.org/dc/elements/1.1/"
        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    """.trimIndent()

    private fun entries(contentXml: String): Map<String, ByteArray> = mapOf(
        "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(),
        "content.xml" to contentXml.toByteArray(),
        "styles.xml" to "<office:document-styles $ns><office:styles/></office:document-styles>".toByteArray()
    )

    private fun parse(contentXml: String) =
        OdtDocumentParser(entries(contentXml)).parseEntries(entries(contentXml))

    @Test
    fun `inline frame with a text box becomes an inline text box with position`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:p>before
                    <draw:frame text:anchor-type="as-char" svg:x="1in" svg:y="2in" draw:z-index="3">
                      <draw:wrap-square/>
                      <draw:text-box><text:p>Inner box</text:p></draw:text-box>
                    </draw:frame>
                  after
                  </text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val box = paragraph.inlines.filterIsInstance<InlineNode.TextBox>().single()
        val inner = box.blocks.single() as BlockNode.Paragraph
        assertEquals("Inner box", inner.inlines.filterIsInstance<InlineNode.Text>().single().text.trim())
        assertEquals(72.0, box.frameStyle.positionXPoints!!, 0.01)
        assertEquals(144.0, box.frameStyle.positionYPoints!!, 0.01)
        assertEquals("square", box.frameStyle.wrapStyle)
        assertEquals(3, box.frameStyle.zOrder)
    }

    @Test
    fun `tracked changes map to revision blocks with author and date`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:tracked-changes>
                    <text:insertion>
                      <office:change-info>
                        <dc:creator>Ann</dc:creator>
                        <dc:date>2024-01-01T10:00:00</dc:date>
                      </office:change-info>
                      <text:p>Inserted text</text:p>
                    </text:insertion>
                    <text:deletion>
                      <office:change-info><dc:creator>Bob</dc:creator></office:change-info>
                      <text:p>Deleted text</text:p>
                    </text:deletion>
                  </text:tracked-changes>
                  <text:p>Stable</text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)

        val insertion = result.document.blocks[0] as BlockNode.RevisionBlock
        assertEquals(RevisionKind.INSERTION, insertion.kind)
        assertEquals("Ann", insertion.author)
        assertEquals("2024-01-01T10:00:00", insertion.date)
        assertTrue(insertion.blocks.first() is BlockNode.Paragraph)

        val deletion = result.document.blocks[1] as BlockNode.RevisionBlock
        assertEquals(RevisionKind.DELETION, deletion.kind)
        assertEquals("Bob", deletion.author)

        assertEquals("Stable", (result.document.blocks[2] as BlockNode.Paragraph).let { p ->
            p.inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        })
    }

    @Test
    fun `inline change markers wrap the changed range in a revision`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:p>keep<text:change-start text:id="cd1"/>changed<text:change-end text:id="cd1"/>tail</text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val revision = paragraph.inlines.filterIsInstance<InlineNode.Revision>().single()
        assertEquals(RevisionKind.INSERTION, revision.kind)
        assertEquals(
            "changed",
            revision.children.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        )
    }

    @Test
    fun `cell padding width and row-span continuation are parsed`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <style:style style:name="ce1" style:family="table-cell">
                  <style:table-cell-properties fo:border="0.5pt solid #000000" fo:padding="0.2in" style:width="2in"/>
                </style:style>
              </office:automatic-styles>
              <office:body>
                <office:text>
                  <table:table table:name="Span">
                    <table:table-column/>
                    <table:table-column/>
                    <table:table-row>
                      <table:table-cell><text:p>top left</text:p></table:table-cell>
                      <table:table-cell table:style-name="ce1" table:number-rows-spanned="2">
                        <text:p>tall</text:p>
                      </table:table-cell>
                    </table:table-row>
                    <table:table-row>
                      <table:table-cell><text:p>bottom left</text:p></table:table-cell>
                    </table:table-row>
                  </table:table>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)

        val table = result.document.blocks.single() as BlockNode.Table
        val cell = table.rows[0].cells[1]
        assertEquals(14.4, cell.style.margins?.top?.widthPoints!!, 0.01) // padding, not border
        assertEquals(0.5, cell.style.borders.top?.widthPoints!!, 0.01)
        assertEquals(144.0, cell.style.widthPoints!!, 0.01)
        assertEquals(VerticalMerge.RESTART, cell.style.verticalMerge)
        // Producer omitted the covered cell; propagation appends a CONTINUE cell.
        assertEquals(VerticalMerge.CONTINUE, table.rows[1].cells[1].style.verticalMerge)
        // The content cell stays untouched.
        assertTrue(table.rows[1].cells[0].blocks.isNotEmpty())
        assertEquals("Span", table.style.caption)
    }
}
