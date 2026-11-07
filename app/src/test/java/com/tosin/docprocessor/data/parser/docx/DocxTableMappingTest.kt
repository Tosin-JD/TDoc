package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTableMappingTest {

    private fun entries(documentXml: String, stylesXml: String? = null): Map<String, ByteArray> {
        val base = mutableMapOf(
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
            "word/document.xml" to documentXml.trimIndent().toByteArray()
        )
        if (stylesXml != null) {
            base["word/styles.xml"] = stylesXml.trimIndent().toByteArray()
        }
        return base
    }

    private val documentWithTable = """
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:tbl>
              <w:tblPr>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="8" w:color="FF0000"/>
                  <w:left w:val="single" w:sz="8" w:color="FF0000"/>
                  <w:bottom w:val="single" w:sz="8" w:color="FF0000"/>
                  <w:right w:val="single" w:sz="8" w:color="FF0000"/>
                </w:tblBorders>
                <w:jc w:val="center"/>
                <w:tblCellMar>
                  <w:left w:w="120" w:type="dxa"/>
                </w:tblCellMar>
              </w:tblPr>
              <w:tblGrid>
                <w:gridCol w:w="2000"/>
                <w:gridCol w:w="3000"/>
              </w:tblGrid>
              <w:tr>
                <w:tc>
                  <w:tcPr>
                    <w:gridSpan w:val="2"/>
                    <w:shd w:fill="D9E2F3"/>
                    <w:vAlign w:val="center"/>
                  </w:tcPr>
                  <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Header</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
              <w:tr>
                <w:trPr><w:trHeight w:val="480"/></w:trPr>
                <w:tc>
                  <w:tcPr><w:vMerge w:val="restart"/></w:tcPr>
                  <w:p><w:r><w:t>Anchor</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:tcPr>
                    <w:tcBorders>
                      <w:bottom w:val="single" w:sz="4" w:color="00FF00"/>
                    </w:tcBorders>
                    <w:tcMar><w:left w:w="120" w:type="dxa"/></w:tcMar>
                  </w:tcPr>
                  <w:p><w:r><w:t>B</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
              <w:tr>
                <w:tc>
                  <w:tcPr><w:vMerge/></w:tcPr>
                  <w:p/>
                </w:tc>
                <w:tc>
                  <w:p><w:r><w:t>C</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>
          </w:body>
        </w:document>
    """

    @Test
    fun `tcPr is never reported as an unsupported opaque block`() {
        val result = DocxDocumentParser(entries(documentWithTable))
            .parseEntries(entries(documentWithTable))

        assertFalse(result.diagnostics.any { it.construct == "w:tcPr" })
        assertFalse(
            result.diagnostics.any {
                it.severity == ParseDiagnostic.Severity.WARNING &&
                    it.construct?.contains("tcPr", ignoreCase = true) == true
            }
        )

        val table = result.document.blocks.filterIsInstance<BlockNode.Table>().single()
        val opaque = table.rows.flatMap { row ->
            row.cells.flatMap { cell -> cell.blocks.filterIsInstance<BlockNode.Opaque>() }
        }
        assertTrue(opaque.isEmpty())
    }

    @Test
    fun `table structure styles and merges reach the adapted document`() {
        val result = DocxDocumentParser(entries(documentWithTable))
            .parseEntries(entries(documentWithTable))

        val table = result.content.filterIsInstance<DocumentElement.Table>().single()
        assertEquals(listOf(100f, 150f), table.gridColumnWidthsPt)
        assertEquals(TextAlignment.CENTER, table.metadata.alignment)
        assertEquals("FF0000", table.metadata.borders.top?.color)
        assertEquals(6.0, table.metadata.cellMarginsPt?.left?.widthPoints ?: 0.0, 0.01)
        assertEquals(3, table.richRows.size)

        val header = table.richRows[0].single()
        assertEquals(2, header.colSpan)
        assertEquals("D9E2F3", header.backgroundColor)
        assertEquals(VerticalCellAlign.CENTER, header.verticalAlignment)
        assertTrue(header.spans.any { it.isBold && it.text == "Header" })

        val anchor = table.richRows[1][0]
        assertEquals(2, anchor.rowSpan)
        assertFalse(anchor.isCovered)
        assertTrue(table.richRows[2][0].isCovered)

        val bottomRight = table.richRows[1][1]
        assertEquals("00FF00", bottomRight.borders?.bottom?.color)
        assertEquals(6.0, bottomRight.margins?.left?.widthPoints ?: 0.0, 0.01)

        assertEquals(24f, table.rowHeightsPt[1], 0.01f)
        assertNotNull(table.metadata)
    }

    @Test
    fun `table style basedOn inherits borders and inside rules`() {
        val styles = """
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="table" w:styleId="TableGrid">
                <w:name w:val="Table Grid"/>
                <w:tblPr>
                  <w:tblBorders>
                    <w:top w:val="single" w:sz="4" w:color="000000"/>
                    <w:left w:val="single" w:sz="4" w:color="000000"/>
                    <w:bottom w:val="single" w:sz="4" w:color="000000"/>
                    <w:right w:val="single" w:sz="4" w:color="000000"/>
                    <w:insideH w:val="single" w:sz="4" w:color="000000"/>
                    <w:insideV w:val="single" w:sz="4" w:color="000000"/>
                  </w:tblBorders>
                </w:tblPr>
              </w:style>
              <w:style w:type="table" w:styleId="CustomGrid">
                <w:name w:val="Custom Grid"/>
                <w:basedOn w:val="TableGrid"/>
              </w:style>
            </w:styles>
        """
        val document = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:tbl>
                  <w:tblPr><w:tblStyle w:val="CustomGrid"/></w:tblPr>
                  <w:tblGrid>
                    <w:gridCol w:w="2000"/>
                    <w:gridCol w:w="2000"/>
                  </w:tblGrid>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>a</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>b</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """
        val entries = entries(document, styles)
        val result = DocxDocumentParser(entries).parseEntries(entries)

        val table = result.content.filterIsInstance<DocumentElement.Table>().single()
        // Outer borders inherited from the TableGrid parent style.
        assertEquals("single", table.metadata.borders.top?.style)
        assertEquals(0.5, table.metadata.borders.top?.widthPoints ?: 0.0, 0.01)
        // insideH/insideV survive the basedOn chain into the presentation.
        assertNotNull(table.presentation?.insideBorders?.top)
        assertNotNull(table.presentation?.insideBorders?.left)

        // Shared-edge ownership: the first cell paints top/left/bottom (table
        // boundary bottom) but not its right edge (owned by column 2's left).
        val cell = table.presentation?.rows?.get(0)?.cells?.get(0)
        assertNotNull(cell?.paintBorders?.top)
        assertNotNull(cell?.paintBorders?.bottom)
        assertNotNull(cell?.paintBorders?.left)
        assertNull(cell?.paintBorders?.right)
    }
}
