package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.data.parser.exception.ParseException
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxRichExtensionsTest {

    private fun packageEntries(
        documentXml: String,
        stylesXml: String? = null,
        numberingXml: String? = null
    ): Map<String, ByteArray> {
        val map = linkedMapOf(
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
            "word/document.xml" to documentXml.toByteArray()
        )
        if (stylesXml != null) map["word/styles.xml"] = stylesXml.toByteArray()
        if (numberingXml != null) map["word/numbering.xml"] = numberingXml.toByteArray()
        return map
    }

    private val w = """xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main""""

    @Test
    fun `drawing text box becomes an inline text box with position wrap and z-order`() {
        val xml = """
            <w:document $w
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
              <w:body>
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:anchor relativeHeight="251658240">
                        <wp:positionH relativeFrom="margin"><wp:posOffset>914400</wp:posOffset></wp:positionH>
                        <wp:positionV relativeFrom="paragraph"><wp:posOffset>457200</wp:posOffset></wp:positionV>
                        <wp:extent cx="1828800" cy="914400"/>
                        <wp:wrapSquare/>
                        <wp:docPr id="1" name="TextBox 1"/>
                        <wps:txbx>
                          <w:txbxContent>
                            <w:p><w:r><w:t>Box text</w:t></w:r></w:p>
                          </w:txbxContent>
                        </wps:txbx>
                      </wp:anchor>
                    </w:drawing>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val box = paragraph.inlines.filterIsInstance<InlineNode.TextBox>().single()
        val inner = box.blocks.single() as BlockNode.Paragraph
        assertEquals("Box text", inner.inlines.filterIsInstance<InlineNode.Text>().single().text)
        assertEquals(72.0, box.frameStyle.positionXPoints!!, 0.01)
        assertEquals(36.0, box.frameStyle.positionYPoints!!, 0.01)
        assertEquals("square", box.frameStyle.wrapStyle)
        assertEquals(251658240, box.frameStyle.zOrder)
    }

    @Test
    fun `inline drawing without anchor keeps inline semantics and no position`() {
        val xml = """
            <w:document $w
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:inline>
                        <wp:extent cx="914400" cy="914400"/>
                        <wp:docPr id="2" name="Picture"/>
                        <a:graphic><a:graphicData><pic:pic><pic:blipFill>
                          <a:blip r:embed="rId99" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                        </pic:blipFill></pic:pic></a:graphicData></a:graphic>
                      </wp:inline>
                    </w:drawing>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val drawing = paragraph.inlines.filterIsInstance<InlineNode.Drawing>().single()
        assertNull(drawing.frameStyle.positionXPoints)
        assertNull(drawing.frameStyle.wrapStyle)
        assertEquals(72.0, drawing.frameStyle.widthPoints!!, 0.01)
    }

    @Test
    fun `vml text box becomes an anchored text box with geometry`() {
        val xml = """
            <w:document $w
                xmlns:v="urn:schemas-microsoft-com:vml">
              <w:body>
                <w:p>
                  <w:r>
                    <w:pict>
                      <v:shape style="width:90pt;height:36pt">
                        <v:textbox>
                          <w:txbxContent>
                            <w:p><w:r><w:t>VML box</w:t></w:r></w:p>
                          </w:txbxContent>
                        </v:textbox>
                      </v:shape>
                    </w:pict>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val box = paragraph.inlines.filterIsInstance<InlineNode.TextBox>().single()
        val inner = box.blocks.single() as BlockNode.Paragraph
        assertEquals("VML box", inner.inlines.filterIsInstance<InlineNode.Text>().single().text)
        assertEquals(90.0, box.frameStyle.widthPoints!!, 0.01)
        assertEquals(36.0, box.frameStyle.heightPoints!!, 0.01)
    }

    @Test
    fun `drawing text box keeps styled paragraphs and a nested table`() {
        val xml = """
            <w:document $w
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
              <w:body>
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:inline>
                        <wp:extent cx="1828800" cy="914400"/>
                        <wps:txbx>
                          <w:txbxContent>
                            <w:p><w:r><w:t>first</w:t></w:r></w:p>
                            <w:p><w:r><w:t>second</w:t></w:r></w:p>
                            <w:tbl>
                              <w:tr><w:tc><w:p><w:r><w:t>nested cell</w:t></w:r></w:p></w:tc></w:tr>
                            </w:tbl>
                          </w:txbxContent>
                        </wps:txbx>
                      </wp:inline>
                    </w:drawing>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val paragraph = result.document.blocks.single() as BlockNode.Paragraph
        val box = paragraph.inlines.filterIsInstance<InlineNode.TextBox>().single()
        assertEquals(3, box.blocks.size)
        assertTrue(box.blocks[0] is BlockNode.Paragraph)
        assertTrue(box.blocks[1] is BlockNode.Paragraph)
        val table = box.blocks[2] as BlockNode.Table
        val cellText = table.rows.single().cells.single().blocks
            .filterIsInstance<BlockNode.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<InlineNode.Text>()
            .joinToString("") { it.text }
        assertEquals("nested cell", cellText)
    }

    @Test
    fun `deeply nested text boxes are capped with a preserve diagnostic`() {
        val nesting = 12
        val open = "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"914400\" cy=\"914400\"/>" +
            "<wps:txbx><w:txbxContent>"
        val close = "</w:txbxContent></wps:txbx></wp:inline></w:drawing></w:r></w:p>"
        val body = StringBuilder().append("<w:body>")
        for (i in 0 until nesting) body.append(open)
        body.append("<w:p><w:r><w:t>deep</w:t></w:r></w:p>")
        for (i in 0 until nesting) body.append(close)
        body.append("</w:body>")
        val xml = """
            <w:document $w
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
              $body
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        assertTrue(
            result.diagnostics.any {
                it.construct == "w:txbxContent" && it.severity == ParseDiagnostic.Severity.WARNING
            }
        )
        // The parse still completes with the outer box intact.
        val outer = (result.document.blocks.single() as BlockNode.Paragraph)
            .inlines.filterIsInstance<InlineNode.TextBox>().single()
        assertTrue(outer.blocks.isNotEmpty())
    }

    @Test
    fun `theme font and tint resolve to concrete run style`() {
        val xml = """
            <w:document $w>
              <w:body><w:p><w:r><w:rPr>
                <w:rFonts w:asciiTheme="minorHAnsi" w:hAnsiTheme="minorHAnsi"/>
                <w:color w:themeColor="accent1" w:themeTint="80"/>
              </w:rPr><w:t>themed</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()
        val packageEntries = packageEntries(xml).toMutableMap()
        packageEntries["word/theme/theme1.xml"] = """
            <a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <a:themeElements>
                <a:fontScheme name="Office">
                  <a:majorFont><a:latin typeface="Major"/></a:majorFont>
                  <a:minorFont><a:latin typeface="Minor"/></a:minorFont>
                </a:fontScheme>
                <a:clrScheme name="Office">
                  <a:accent1><a:srgbClr val="336699"/></a:accent1>
                </a:clrScheme>
              </a:themeElements>
            </a:theme>
        """.trimIndent().toByteArray()

        val parsed = DocxDocumentParser(packageEntries).parseEntries(packageEntries)
        val paragraph = parsed.document.blocks.single() as BlockNode.Paragraph
        val style = paragraph.inlines.filterIsInstance<InlineNode.Text>().single().style
        assertEquals("Minor", style.fontAscii)
        assertEquals("Minor", style.fontHAnsi)
        assertEquals("99B2CC", style.color)
    }

    @Test
    fun `encrypted docx is rejected clearly and glossary parts are preserved`() {
        val error = try {
            DocxDocumentParser(mapOf("EncryptionInfo" to byteArrayOf(1))).parseEntries(
                mapOf("EncryptionInfo" to byteArrayOf(1))
            )
            null
        } catch (exception: ParseException) {
            exception
        }
        assertNotNull(error)
        assertEquals("Encrypted DOCX documents are not supported", error?.message)

        val entries = packageEntries("<w:document $w><w:body/></w:document>").toMutableMap()
        entries["word/glossary/document.xml"] = "<w:glossaryDocument/>".toByteArray()
        val parsed = DocxDocumentParser(entries).parseEntries(entries)
        assertEquals("glossary", parsed.document.opaqueParts.single().role)
    }

    @Test
    fun `section extras and page backgrounds are parsed for in-flow sections`() {
        val xml = """
            <w:document $w xmlns:v="urn:schemas-microsoft-com:vml"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:background w:fill="ABCDEF"><v:background><v:fill r:id="bg"/></v:background></w:background>
              <w:body>
                <w:p><w:r><w:t>Section content</w:t></w:r></w:p>
                <w:sectPr>
                  <w:pgSz w:w="12240" w:h="15840"/>
                  <w:pgBorders><w:top w:val="single" w:sz="8" w:color="000000"/></w:pgBorders>
                  <w:lnNumType w:countBy="2"/>
                  <w:cols w:num="2" w:equalWidth="0">
                    <w:col w:w="3000"/><w:col w:w="5000"/>
                  </w:cols>
                  <w:footnotePr w:pos="pageBottom"/>
                  <w:vAlign w:val="center"/>
                  <w:pgNumType w:start="4"/>
                </w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent()
        val packageEntries = packageEntries(xml).toMutableMap()
        packageEntries["word/_rels/document.xml.rels"] = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="bg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/bg.png"/>
            </Relationships>
        """.trimIndent().toByteArray()
        packageEntries["word/media/bg.png"] = byteArrayOf(8, 9, 10)

        val result = DocxDocumentParser(packageEntries).parseEntries(packageEntries)
        val section = result.document.blocks.filterIsInstance<BlockNode.SectionBreak>().single()
        assertEquals(2, section.style.page.columns)
        assertEquals(listOf(150.0, 250.0), section.style.page.columnWidths)
        assertEquals(2, section.style.page.lineNumberingStep)
        assertEquals("pageBottom", section.style.page.notePlacement)
        assertEquals("center", section.style.page.verticalAlign)
        assertEquals(4, section.style.pageNumberStart)
        assertEquals("ABCDEF", section.style.page.background)
        assertEquals("word/media/bg.png", section.style.page.backgroundImageResourceId)
        assertEquals(1.0, section.style.page.pageBorders?.top?.widthPoints!!, 0.01)
    }

    @Test
    fun `chart title and embedded object ProgID are preserved in opaque metadata`() {
        val xml = """
            <w:document $w xmlns:o="urn:schemas-microsoft-com:office:office"
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body><w:p>
                <w:r><w:drawing><wp:inline><a:graphic><a:graphicData><c:chart r:id="chart1"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>
                <w:r><w:object><o:OLEObject ProgID="Excel.Sheet.12" ShapeID="SheetObject"/></w:object></w:r>
              </w:p></w:body>
            </w:document>
        """.trimIndent()
        val packageEntries = packageEntries(xml).toMutableMap()
        packageEntries["word/_rels/document.xml.rels"] = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="chart1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="charts/chart1.xml"/>
            </Relationships>
        """.trimIndent().toByteArray()
        packageEntries["word/charts/chart1.xml"] = """
            <c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <c:chart><c:title><c:tx><c:rich><a:p><a:r><a:t>Revenue</a:t></a:r></a:p></c:rich></c:tx></c:title></c:chart>
            </c:chartSpace>
        """.trimIndent().toByteArray()

        val result = DocxDocumentParser(packageEntries).parseEntries(packageEntries)
        val inlines = (result.document.blocks.single() as BlockNode.Paragraph).inlines
        val chart = inlines.filterIsInstance<InlineNode.Opaque>().first()
        val ole = inlines.filterIsInstance<InlineNode.Opaque>().last()
        assertTrue(chart.node.reason.orEmpty().contains("Revenue"))
        assertTrue(ole.node.reason.orEmpty().contains("ProgID=Excel.Sheet.12"))
    }

    @Test
    fun `cell margins and horizontal merges are parsed`() {
        val xml = """
            <w:document $w>
              <w:body>
                <w:tbl>
                  <w:tblPr>
                    <w:tblCellMar>
                      <w:top w:w="60" w:type="dxa"/>
                      <w:left w:w="108" w:type="dxa"/>
                      <w:bottom w:w="60" w:type="dxa"/>
                      <w:right w:w="108" w:type="dxa"/>
                    </w:tblCellMar>
                  </w:tblPr>
                  <w:tr>
                    <w:tc>
                      <w:tcPr><w:hMerge w:val="restart"/></w:tcPr>
                      <w:p><w:r><w:t>anchor</w:t></w:r></w:p>
                    </w:tc>
                    <w:tc>
                      <w:tcPr><w:hMerge w:val="continue"/></w:tcPr>
                      <w:p><w:r><w:t>covered1</w:t></w:r></w:p>
                    </w:tc>
                    <w:tc>
                      <w:tcPr><w:hMerge w:val="continue"/></w:tcPr>
                      <w:p><w:r><w:t>covered2</w:t></w:r></w:p>
                    </w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val table = result.document.blocks.single() as BlockNode.Table
        // All three cells are one horizontal merge → one anchor cell with span 3.
        assertEquals(1, table.rows.single().cells.size)
        assertEquals(3, table.rows.single().cells[0].style.gridSpan)
        assertEquals(5.4, table.style.cellMargins?.left?.widthPoints!!, 0.01)
        assertEquals(3.0, table.style.cellMargins?.top?.widthPoints!!, 0.01)
    }

    @Test
    fun `block-level moves and run format changes become revision nodes`() {
        val xml = """
            <w:document $w>
              <w:body>
                <w:moveFrom w:author="Ann" w:date="2024-01-01T00:00:00Z">
                  <w:p><w:r><w:t>moved away</w:t></w:r></w:p>
                </w:moveFrom>
                <w:p>
                  <w:r>
                    <w:rPr><w:rPrChange w:author="Bob" w:date="2024-02-01T00:00:00Z"><w:rPr/></w:rPrChange></w:rPr>
                    <w:t>reformatted</w:t>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val move = result.document.blocks[0] as BlockNode.RevisionBlock
        assertEquals(RevisionKind.MOVE_FROM, move.kind)
        assertEquals("Ann", move.author)
        assertTrue(move.blocks.first() is BlockNode.Paragraph)

        val paragraph = result.document.blocks[1] as BlockNode.Paragraph
        val revision = paragraph.inlines.single() as InlineNode.Revision
        assertEquals(RevisionKind.FORMAT_CHANGE, revision.kind)
        assertEquals("Bob", revision.author)
        assertTrue(revision.children.first() is InlineNode.Text)
    }

    @Test
    fun `table style contributes borders and banding flags`() {
        val styles = """
            <w:styles $w>
              <w:style w:type="table" w:styleId="Grid">
                <w:name w:val="Table Grid"/>
                <w:tblPr>
                  <w:tblBorders>
                    <w:top w:val="single" w:sz="8" w:color="FF0000"/>
                    <w:left w:val="single" w:sz="8" w:color="FF0000"/>
                    <w:bottom w:val="single" w:sz="8" w:color="FF0000"/>
                    <w:right w:val="single" w:sz="8" w:color="FF0000"/>
                    <w:insideH w:val="single" w:sz="4" w:color="000000"/>
                    <w:insideV w:val="single" w:sz="4" w:color="000000"/>
                  </w:tblBorders>
                </w:tblPr>
                <w:tblStylePr w:type="firstRow"/>
                <w:tblStylePr w:type="band1Horz"/>
              </w:style>
            </w:styles>
        """.trimIndent()
        val xml = """
            <w:document $w>
              <w:body>
                <w:tbl>
                  <w:tblPr><w:tblStyle w:val="Grid"/></w:tblPr>
                  <w:tr><w:tc><w:p><w:r><w:t>c</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml, stylesXml = styles))
            .parseEntries(packageEntries(xml, stylesXml = styles))

        val table = result.document.blocks.single() as BlockNode.Table
        assertEquals("single", table.style.borders.top?.style)
        assertEquals(1.0, table.style.borders.top?.widthPoints!!, 0.01)
        assertEquals("FF0000", table.style.borders.top?.color)
        assertTrue(table.style.banding.firstRow)
        assertTrue(table.style.banding.band1)
        // The style catalogue exposes the same resolved banding.
        assertTrue(result.document.styles.tableStyles.getValue("Grid").banding.firstRow)
    }

    @Test
    fun `vertical merge continue cells keep their semantics`() {
        val xml = """
            <w:document $w>
              <w:body>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(packageEntries(xml)).parseEntries(packageEntries(xml))

        val table = result.document.blocks.single() as BlockNode.Table
        assertEquals(VerticalMerge.RESTART, table.rows[0].cells[0].style.verticalMerge)
        assertEquals(VerticalMerge.CONTINUE, table.rows[1].cells[0].style.verticalMerge)
        assertNotNull(table.rows[1].cells[0])
    }
}
