package com.tosin.docprocessor.data.parser.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType

class DocxFieldParserTest {

    private val parser = DocxFieldParser()

    @Test
    fun testParseComplexPageField() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            val beginRun = paragraph.createRun()
            beginRun.ctr.addNewFldChar().fldCharType = STFldCharType.BEGIN
            val instrRun = paragraph.createRun()
            instrRun.ctr.addNewInstrText().stringValue = " PAGE "
            val endRun = paragraph.createRun()
            endRun.ctr.addNewFldChar().fldCharType = STFldCharType.END

            val fields = parser.parse(paragraph)
            assertEquals(1, fields.size)
            val field = fields.first()
            assertEquals("page-number", field.info.type)
            assertEquals("PAGE", field.info.instruction)
            assertFalse(field.info.isSimpleField)
            assertEquals("PAGE", field.info.arguments["pageField"])
        }
    }

    @Test
    fun testParseComplexTitleField() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            val beginRun = paragraph.createRun()
            beginRun.ctr.addNewFldChar().fldCharType = STFldCharType.BEGIN
            val instrRun = paragraph.createRun()
            instrRun.ctr.addNewInstrText().stringValue = " TITLE "
            val endRun = paragraph.createRun()
            endRun.ctr.addNewFldChar().fldCharType = STFldCharType.END

            val fields = parser.parse(paragraph)
            val field = fields.first()
            assertEquals("title", field.info.type)
            assertEquals("title", field.info.arguments["property"])
        }
    }

    @Test
    fun testParseSimpleMergeField() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewFldSimple().instr = " MERGEFIELD  Name "

            val fields = parser.parse(paragraph)
            val field = fields.first()
            assertEquals("merge-field", field.info.type)
            assertTrue(field.info.isSimpleField)
            assertEquals("Name", field.info.arguments["fieldName"])
        }
    }

    @Test
    fun testParseSimpleHyperlink() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewFldSimple().instr = " HYPERLINK \"https://example.com\" "

            val field = parser.parse(paragraph).first()
            assertEquals("hyperlink", field.info.type)
            assertEquals("https://example.com", field.info.arguments["target"])
        }
    }

    @Test
    fun testParseSimpleToc() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewFldSimple().instr = " TOC \\o \"1-3\" \\h \\z \\u "

            val field = parser.parse(paragraph).first()
            assertEquals("toc", field.info.type)
            assertTrue("switches should include \\h", field.info.arguments["switches"].orEmpty().contains("\\h"))
        }
    }

    @Test
    fun testParseInlineDateSimpleField() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewFldSimple().instr = " DATE \\@ \"yyyy-MM-dd\" "

            val field = parser.parse(paragraph).first()
            assertEquals("date-time", field.info.type)
            assertEquals("date", field.info.arguments["property"])
        }
    }
}