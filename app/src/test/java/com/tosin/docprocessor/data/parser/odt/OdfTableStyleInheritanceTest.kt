package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.odt.resolve.OdfParagraphProperties
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OdfTableStyleInheritanceTest {

    private val ns = """
        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
        xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    """.trimIndent()

    private fun entries(contentXml: String, stylesXml: String): Map<String, ByteArray> = mapOf(
        "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(),
        "content.xml" to contentXml.toByteArray(),
        "styles.xml" to stylesXml.toByteArray()
    )

    // ------------------------------------------------------------------
    // fo:border shorthand parsing
    // ------------------------------------------------------------------

    @Test
    fun `border shorthand accepts tokens in any order`() {
        val border = OdfParagraphProperties.parseBorder("solid 0.5pt #FF0000")
        assertEquals("solid", border.style)
        assertEquals(0.5, border.widthPoints!!, 0.001)
        assertEquals("#FF0000", border.color)
    }

    @Test
    fun `border shorthand parses dashed style with named colour`() {
        val border = OdfParagraphProperties.parseBorder("1pt dashed red")
        assertEquals("dashed", border.style)
        assertEquals(1.0, border.widthPoints!!, 0.001)
        assertEquals("#FF0000", border.color)
    }

    @Test
    fun `border shorthand parses rgb colour containing spaces`() {
        val border = OdfParagraphProperties.parseBorder("0.5pt solid rgb(255, 0, 0)")
        assertEquals(0.5, border.widthPoints!!, 0.001)
        assertEquals("#FF0000", border.color)
    }

    @Test
    fun `border shorthand maps width keywords to points`() {
        val border = OdfParagraphProperties.parseBorder("thick double black")
        assertEquals("double", border.style)
        assertEquals(3.75, border.widthPoints!!, 0.001)
        assertEquals("#000000", border.color)
    }

    @Test
    fun `border shorthand none anywhere is an explicit no-paint marker`() {
        val border = OdfParagraphProperties.parseBorder("0.5pt none #FF0000")
        assertEquals("none", border.style)
        assertEquals(0.0, border.widthPoints!!, 0.001)
    }

    // ------------------------------------------------------------------
    // style:parent-style-name inheritance for table and cell styles
    // ------------------------------------------------------------------

    private val stylesWithInheritance = """
        <office:document-styles $ns>
          <office:styles>
            <style:style style:name="CellBase" style:family="table-cell">
              <style:table-cell-properties fo:border="0.5pt solid #FF0000"
                  fo:background-color="#EEEEEE"/>
            </style:style>
            <style:style style:name="CellDerived" style:family="table-cell"
                style:parent-style-name="CellBase">
              <style:table-cell-properties style:vertical-align="middle"/>
            </style:style>
            <style:style style:name="TableBase" style:family="table">
              <style:table-properties style:width="3in" table:align="center"
                  fo:border="1pt solid #0000FF"/>
            </style:style>
            <style:style style:name="TableDerived" style:family="table"
                style:parent-style-name="TableBase">
              <style:table-properties fo:background-color="#FFFF00"/>
            </style:style>
          </office:styles>
        </office:document-styles>
    """.trimIndent()

    private val contentWithStyledTable = """
        <office:document-content $ns>
          <office:automatic-styles/>
          <office:body>
            <office:text>
              <table:table text:style-name="TableDerived">
                <table:table-row>
                  <table:table-cell table:style-name="CellDerived">
                    <text:p>cell</text:p>
                  </table:table-cell>
                </table:table-row>
              </table:table>
            </office:text>
          </office:body>
        </office:document-content>
    """.trimIndent()

    @Test
    fun `table style inherits parent properties`() {
        val result = OdtDocumentParser(
            entries(contentWithStyledTable, stylesWithInheritance)
        ).parseEntries(entries(contentWithStyledTable, stylesWithInheritance))

        val table = result.document.blocks.filterIsInstance<BlockNode.Table>().single()
        assertEquals(216.0, table.style.widthPoints!!, 0.01) // 3in
        assertEquals(com.tosin.docprocessor.domain.document.model.style.TextAlignment.CENTER, table.style.alignment)
        assertEquals("#FFFF00", table.style.shading?.fill)
        // Inherited from TableBase.
        assertEquals("#0000FF", table.style.borders.top?.color)
        assertEquals(1.0, table.style.borders.top?.widthPoints!!, 0.001)
    }

    @Test
    fun `cell style inherits parent borders and shading`() {
        val result = OdtDocumentParser(
            entries(contentWithStyledTable, stylesWithInheritance)
        ).parseEntries(entries(contentWithStyledTable, stylesWithInheritance))

        val table = result.document.blocks.filterIsInstance<BlockNode.Table>().single()
        val cell = table.rows.single().cells.single()
        assertEquals(VerticalCellAlign.CENTER, cell.style.verticalAlign)
        assertEquals("#EEEEEE", cell.style.shading?.fill)
        assertEquals("#FF0000", cell.style.borders.top?.color)
        assertEquals(0.5, cell.style.borders.top?.widthPoints!!, 0.001)
        assertNotNull(cell.style.borders.bottom)
    }

    @Test
    fun `child border overrides only its own sides`() {
        val styles = """
            <office:document-styles $ns>
              <office:styles>
                <style:style style:name="CellBase" style:family="table-cell">
                  <style:table-cell-properties fo:border="0.5pt solid #FF0000"/>
                </style:style>
                <style:style style:name="CellChild" style:family="table-cell"
                    style:parent-style-name="CellBase">
                  <style:table-cell-properties fo:border-bottom="2pt dashed #00FF00"/>
                </style:style>
              </office:styles>
            </office:document-styles>
        """.trimIndent()
        val content = """
            <office:document-content $ns>
              <office:automatic-styles/>
              <office:body>
                <office:text>
                  <table:table>
                    <table:table-row>
                      <table:table-cell table:style-name="CellChild">
                        <text:p>x</text:p>
                      </table:table-cell>
                    </table:table-row>
                  </table:table>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val result = OdtDocumentParser(entries(content, styles))
            .parseEntries(entries(content, styles))

        val cell = result.document.blocks.filterIsInstance<BlockNode.Table>()
            .single().rows.single().cells.single()
        // Child's explicit bottom wins; other sides inherit the parent's uniform border.
        assertEquals("dashed", cell.style.borders.bottom?.style)
        assertEquals(2.0, cell.style.borders.bottom?.widthPoints!!, 0.001)
        assertEquals("#00FF00", cell.style.borders.bottom?.color)
        assertEquals("#FF0000", cell.style.borders.top?.color)
        assertNotNull(cell.style.borders.left)
    }
}
