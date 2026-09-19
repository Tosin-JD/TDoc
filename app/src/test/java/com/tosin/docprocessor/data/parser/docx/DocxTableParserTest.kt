package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class DocxTableParserTest {

    private val parser = DocxTableParser()

    private fun tableOf(block: XWPFTable.() -> Unit): DocumentElement.Table {
        XWPFDocument().use { document ->
            val table = document.createTable(2, 2)
            table.getRow(0).getCell(0).setText("a")
            table.getRow(0).getCell(1).setText("b")
            table.getRow(1).getCell(0).setText("c")
            table.getRow(1).getCell(1).setText("d")
            table.block()
            return parser.parse(table)
        }
    }

    @Test
    fun testParseSimpleTable() {
        val parsed = tableOf { }
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), parsed.rows)
    }

    @Test
    fun testParseTableWithHeader() {
        val parsed = tableOf { getRow(0).isRepeatHeader = true }
        assertTrue(parsed.hasHeader)
    }

    @Test
    fun testParseTableWithCellShading() {
        val parsed = tableOf {
            val cell = getRow(1).getCell(0)
            val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
            tcPr.addNewShd().fill = "CCCCCC"
        }
        assertEquals("CCCCCC", parsed.metadata.rows[1][0].shadingColor)
    }

    @Test
    fun testParseTableWithMergedCells() {
        val parsed = tableOf {
            val cell = getRow(0).getCell(0)
            val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
            tcPr.addNewGridSpan().`val` = BigInteger.valueOf(2)
        }
        assertEquals(2, parsed.metadata.rows[0][0].gridSpan)
    }

    @Test
    fun testParseTableWithBorders() {
        val parsed = tableOf {
            val tblPr = ctTbl.tblPr ?: ctTbl.addNewTblPr()
            val borders = tblPr.tblBorders ?: tblPr.addNewTblBorders()
            (borders.top ?: borders.addNewTop()).color = "FF0000"
        }
        val summary = parsed.metadata.borderSummary
        assertNotNull("table borders should produce a border summary", summary)
        assertTrue("border summary should mention top border", summary.orEmpty().contains("top:FF0000"))
    }

    @Test
    fun testParseTableWithCaption() {
        val parsed = tableOf {
            val tblPr = ctTbl.tblPr ?: ctTbl.addNewTblPr()
            tblPr.addNewTblCaption().`val` = "Table Caption"
        }
        assertEquals("Table Caption", parsed.metadata.caption)
    }

    @Test
    fun testParseTableWithCellBorders() {
        val parsed = tableOf {
            val cell = getRow(0).getCell(0)
            val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
            val tcBorders = tcPr.tcBorders ?: tcPr.addNewTcBorders()
            (tcBorders.bottom ?: tcBorders.addNewBottom()).color = "00AA00"
        }
        val summary = parsed.metadata.rows[0][0].borderSummary
        assertNotNull("cell borders should produce a border summary", summary)
        assertTrue("cell border summary should mention bottom border", summary.orEmpty().contains("bottom:00AA00"))
    }
}