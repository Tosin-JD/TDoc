package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun parse(
        contentXml: String,
        stylesXml: String,
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): com.tosin.docprocessor.domain.document.ParseResult {
        val packageEntries = entries(contentXml).toMutableMap()
        packageEntries["styles.xml"] = stylesXml.toByteArray()
        packageEntries.putAll(extraEntries)
        return OdtDocumentParser(packageEntries).parseEntries(packageEntries)
    }

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
        assertNull(box.frameStyle.positionXPoints)
        assertNull(box.frameStyle.positionYPoints)
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
    fun `unmatched change boundary logs a preserve diagnostic`() {
        val content = """
            <office:document-content $ns>
              <office:body>
                <office:text>
                  <text:p>before<text:change-end text:id="orphan"/>after</text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)

        // The orphan boundary is skipped, not turned into an empty revision.
        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        assertEquals(
            "beforeafter",
            paragraph.inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        )
        assertTrue(
            result.diagnostics.any {
                it.construct == "text:change-end" && it.severity == ParseDiagnostic.Severity.WARNING
            }
        )
    }

    @Test
    fun `cell padding width and row-span continuation are parsed`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <style:style style:name="ce1" style:family="table-cell">
                  <style:table-cell-properties fo:border="0.5pt solid #000000" fo:padding="0.2in" style:width="2in" fo:text-direction="rl"/>
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
        assertEquals("rl", cell.style.textDirection)
        assertEquals(VerticalMerge.RESTART, cell.style.verticalMerge)
        // Producer omitted the covered cell; propagation appends a CONTINUE cell.
        assertEquals(VerticalMerge.CONTINUE, table.rows[1].cells[1].style.verticalMerge)
        // The content cell stays untouched.
        assertTrue(table.rows[1].cells[0].blocks.isNotEmpty())
        assertEquals("Span", table.style.caption)
    }

    @Test
    fun `odf paragraph tabs direction ruby and index entries are mapped`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <style:style style:name="p1" style:family="paragraph">
                  <style:paragraph-properties fo:keep-together="always" fo:direction="rtl">
                    <style:tab-stops>
                      <style:tab-stop style:position="1in" style:type="center" style:leader-style="dotted"/>
                    </style:tab-stops>
                  </style:paragraph-properties>
                </style:style>
              </office:automatic-styles>
              <office:body><office:text>
                <text:p text:style-name="p1">
                  <text:ruby><text:ruby-base>base</text:ruby-base><text:ruby-text>ruby</text:ruby-text></text:ruby>
                </text:p>
                <text:table-of-content>
                  <text:index-body>
                    <text:index-title><text:p>Contents</text:p></text:index-title>
                    <text:index-entry><text:index-entry-text>First heading</text:index-entry-text></text:index-entry>
                    <text:index-entry><text:index-entry-text>Second heading</text:index-entry-text></text:index-entry>
                  </text:index-body>
                </text:table-of-content>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content)
        val paragraph = result.document.blocks[0] as BlockNode.Paragraph
        val ruby = paragraph.inlines.filterIsInstance<InlineNode.Ruby>().single()
        assertEquals("base", ruby.base.filterIsInstance<InlineNode.Text>().joinToString("") { it.text })
        assertEquals("ruby", ruby.rubyText.filterIsInstance<InlineNode.Text>().joinToString("") { it.text })
        assertEquals(true, paragraph.style.keepLines)
        assertEquals(72.0, paragraph.style.tabs.single().positionPoints, 0.01)
        assertEquals("center", paragraph.style.tabs.single().alignment)
        assertEquals("rtl", (paragraph.style.defaultRunStyle ?: error("Expected run direction")).direction)

        val index = result.document.blocks[1] as BlockNode.IndexBlock
        assertEquals("Contents", index.title)
        assertEquals(listOf("First heading", "Second heading"), index.entries)
    }

    @Test
    fun `odf master page changes emit section breaks and bind page geometry`() {
        val styles = """
            <office:document-styles $ns>
              <office:automatic-styles>
                <style:page-layout style:name="layout1">
                  <style:page-layout-properties fo:page-width="8.5in" fo:page-height="11in"
                    fo:background-color="#eeeeee"/>
                </style:page-layout>
                <style:page-layout style:name="layout2">
                  <style:page-layout-properties fo:page-width="6in" fo:page-height="9in"/>
                </style:page-layout>
              </office:automatic-styles>
              <office:master-styles>
                <style:master-page style:name="First" style:page-layout-name="layout1">
                  <style:background-image xlink:href="Pictures/page.png"/>
                </style:master-page>
                <style:master-page style:name="Second" style:page-layout-name="layout2"/>
              </office:master-styles>
            </office:document-styles>
        """.trimIndent()
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <style:style style:name="p1" style:family="paragraph" style:master-page-name="First"/>
                <style:style style:name="p2" style:family="paragraph" style:master-page-name="Second"/>
              </office:automatic-styles>
              <office:body><office:text>
                <text:p text:style-name="p1">First section</text:p>
                <text:p text:style-name="p2">Second section</text:p>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content, styles, mapOf("Pictures/page.png" to byteArrayOf(3, 2, 1)))
        val section = result.document.blocks.first() as BlockNode.SectionBreak
        assertEquals("First", section.style.masterPageName)
        assertEquals(612.0, section.style.page.widthPoints!!, 0.01)
        assertEquals("#eeeeee", section.style.page.background)
        assertEquals("Pictures/page.png", section.style.page.backgroundImageResourceId)
        assertEquals(2, result.document.blocks.filterIsInstance<BlockNode.Paragraph>().size)
        val breaks = result.document.blocks.filterIsInstance<BlockNode.SectionBreak>()
        assertEquals(2, breaks.size)
        assertEquals("Second", breaks[1].style.masterPageName)
        assertEquals(432.0, breaks[1].style.page.widthPoints!!, 0.01)
    }

    @Test
    fun `odf list restart resets numbering and honors explicit start`() {
        val content = """
            <office:document-content $ns>
              <office:automatic-styles>
                <text:list-style style:name="numbered">
                  <text:list-level-style-number text:level="1" text:num-format="1" style:num-suffix="."/>
                </text:list-style>
              </office:automatic-styles>
              <office:body><office:text>
                <text:list text:style-name="numbered"><text:list-item><text:p>one</text:p></text:list-item></text:list>
                <text:list text:style-name="numbered"><text:list-item><text:p>two</text:p></text:list-item></text:list>
                <text:list text:style-name="numbered" text:restart="true" text:start="5">
                  <text:list-item><text:p>five</text:p></text:list-item>
                </text:list>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val lists = parse(content).document.blocks.filterIsInstance<BlockNode.ListBlock>()
        assertEquals(listOf("1.", "2.", "5."), lists.map { it.items.single().marker?.text })
    }

    @Test
    fun `odf settings and figure captions are retained`() {
        val content = """
            <office:document-content $ns>
              <office:body><office:text>
                <draw:frame>
                  <draw:image xlink:href="Pictures/image.png"/>
                  <draw:caption><text:p>Figure caption</text:p></draw:caption>
                </draw:frame>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()
        val packageEntries = entries(content).toMutableMap()
        packageEntries["Pictures/image.png"] = byteArrayOf(1, 2, 3)
        packageEntries["settings.xml"] = """
            <office:document-settings $ns
                xmlns:config="urn:oasis:names:tc:opendocument:xmlns:config:1.0">
              <office:settings><config:config-item-set config:name="ooo:configuration-settings">
                <config:config-item config:name="DefaultTabStop" config:type="double">0.5"</config:config-item>
              </config:config-item-set></office:settings>
            </office:document-settings>
        """.trimIndent().toByteArray()

        val result = OdtDocumentParser(packageEntries).parseEntries(packageEntries)
        val figure = result.document.blocks.single() as BlockNode.Figure
        assertEquals(
            "Figure caption",
            figure.caption.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        )
        assertEquals(36.0, result.document.settings.defaultTabStopPoints!!, 0.01)
    }

    @Test
    fun `odf section styles bind master pages and definition lists stay structured`() {
        val styles = """
            <office:document-styles $ns>
              <office:automatic-styles>
                <style:page-layout style:name="layout1">
                  <style:page-layout-properties fo:page-width="8.5in"/>
                </style:page-layout>
              </office:automatic-styles>
              <office:styles>
                <style:style style:name="sectionStyle" style:family="section" style:master-page-name="First"/>
              </office:styles>
              <office:master-styles>
                <style:master-page style:name="First" style:page-layout-name="layout1"/>
              </office:master-styles>
            </office:document-styles>
        """.trimIndent()
        val content = """
            <office:document-content $ns>
              <office:body><office:text>
                <text:section text:style-name="sectionStyle">
                  <text:p>Section paragraph</text:p>
                </text:section>
                <text:definition-list>
                  <text:definition>
                    <text:term><text:p>Term</text:p></text:term>
                    <text:definition><text:p>Definition</text:p></text:definition>
                  </text:definition>
                </text:definition-list>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val result = parse(content, styles)
        val sectionBreak = result.document.blocks.first() as BlockNode.SectionBreak
        assertEquals("First", sectionBreak.style.masterPageName)
        assertEquals(612.0, sectionBreak.style.page.widthPoints!!, 0.01)
        val list = result.document.blocks.filterIsInstance<BlockNode.ListBlock>().single()
        val itemText = list.items.single().blocks.filterIsInstance<BlockNode.Paragraph>()
            .flatMap { it.inlines }.filterIsInstance<InlineNode.Text>().joinToString("") { it.text }
        assertTrue(itemText.contains("Term"))
        assertTrue(itemText.contains("Definition"))
    }

    @Test
    fun `odf charts and embedded objects keep descriptive opaque metadata`() {
        val content = """
            <office:document-content $ns>
              <office:body><office:text>
                <draw:frame><draw:chart><svg:title>Quarterly sales</svg:title></draw:chart></draw:frame>
                <draw:frame><draw:object draw:class-id="com.example.Sheet"/></draw:frame>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val opaque = parse(content).document.blocks.filterIsInstance<BlockNode.Opaque>()
        assertEquals(2, opaque.size)
        assertTrue(opaque[0].node.reason.orEmpty().contains("Quarterly sales"))
        assertTrue(opaque[1].node.reason.orEmpty().contains("class-id=com.example.Sheet"))
    }
}
