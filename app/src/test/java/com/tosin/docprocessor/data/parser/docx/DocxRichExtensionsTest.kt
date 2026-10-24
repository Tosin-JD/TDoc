package com.tosin.docprocessor.data.parser.docx

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
