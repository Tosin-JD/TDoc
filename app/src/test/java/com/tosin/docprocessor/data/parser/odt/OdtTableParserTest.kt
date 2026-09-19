package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OdtTableParserTest {

    private val automaticStyles = """
<style:style style:name="Table1" style:family="table">
 <style:table-properties fo:background-color="#EEEEEE"/>
</style:style>
<style:style style:name="Cell1" style:family="table-cell">
 <style:table-cell-properties fo:background-color="#CCCCCC" fo:margin-left="0.5cm"/>
</style:style>
"""

    private val contentXml = OdtTestFixtures.contentXmlRoot(
        automaticStyles = automaticStyles,
        bodyChildren = """
<table:table table:name="T1" table:style-name="Table1">
 <table:table-header-rows>
  <table:table-row>
   <table:table-cell table:style-name="Cell1"><text:p>A</text:p></table:table-cell>
   <table:table-cell><text:p>B</text:p></table:table-cell>
  </table:table-row>
 </table:table-header-rows>
 <table:table-row>
  <table:table-cell table:number-columns-spanned="2"><text:p>CD</text:p></table:table-cell>
 </table:table-row>
</table:table>
"""
    )

    private val table: DocumentElement.Table by lazy {
        val zipBytes = OdtTestFixtures.odtZip(contentXml = contentXml)
        val entries = OdtZipExtractor().extractAllEntries(zipBytes.inputStream())
        val cacheDir: File = Files.createTempDirectory("odt-table-test").toFile()
        val elements = OdtXmlParser(cacheDir, entries).parse(entries.getValue("content.xml"))
        elements.filterIsInstance<DocumentElement.Table>().first()
    }

    @Test
    fun testParseSimpleTable() {
        assertEquals(listOf(listOf("A", "B"), listOf("CD")), table.rows)
    }

    @Test
    fun testParseTableWithHeader() {
        assertTrue(table.hasHeader)
    }

    @Test
    fun testParseTableShading() {
        assertEquals("EEEEEE", table.metadata.shadingColor)
        assertEquals("CCCCCC", table.metadata.rows[0][0].shadingColor)
    }

    @Test
    fun testParseCellMargins() {
        val margins = table.metadata.rows[0][0].margins
        assertTrue((margins.left ?: 0) > 0)
        assertEquals(283, margins.left)
    }

    @Test
    fun testParseMergedCells() {
        assertEquals(2, table.metadata.rows[1][0].gridSpan)
    }

    @Test
    fun testParseNestedTable() {
        val nestedContent = OdtTestFixtures.contentXmlRoot(
            automaticStyles = "",
            bodyChildren = """
<table:table>
 <table:table-row>
  <table:table-cell>
   <text:p>Outer</text:p>
   <table:table>
    <table:table-row>
     <table:table-cell><text:p>Inner</text:p></table:table-cell>
    </table:table-row>
   </table:table>
  </table:table-cell>
 </table:table-row>
</table:table>
"""
        )
        val zipBytes = OdtTestFixtures.odtZip(contentXml = nestedContent)
        val entries = OdtZipExtractor().extractAllEntries(zipBytes.inputStream())
        val cacheDir: File = Files.createTempDirectory("odt-nested-test").toFile()
        val nestedTable = OdtXmlParser(cacheDir, entries)
            .parse(entries.getValue("content.xml"))
            .filterIsInstance<DocumentElement.Table>()
            .first()
        assertEquals(1, nestedTable.metadata.rows[0][0].nestedTableCount)
    }
}