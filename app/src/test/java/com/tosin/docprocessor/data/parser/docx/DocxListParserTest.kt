package com.tosin.docprocessor.data.parser.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DocxListParserTest {

    private val document: XWPFDocument by lazy {
        XWPFDocument(ByteArrayInputStream(DocxTestFixtures.listsDocx()))
    }

    private val parser = DocxListParser()

    @Test
    fun testDecimalListLabelFromLevelText() {
        val paragraph = document.paragraphs[0]
        assertEquals("1.", parser.getListLabel(paragraph))
        val info = parser.parseListInfo(paragraph)
        assertEquals(0, info?.level)
        assertEquals("decimal", info?.format)
        assertEquals("1.", info?.levelText)
    }

    @Test
    fun testSubLevelListLabelFromLevelText() {
        val paragraph = document.paragraphs[1]
        assertEquals("i.", parser.getListLabel(paragraph))
        assertEquals(1, parser.parseListInfo(paragraph)?.level)
        assertEquals("lowerRoman", parser.parseListInfo(paragraph)?.format)
    }

    @Test
    fun testBulletListFallsBackToBulletCharacter() {
        val paragraph = document.paragraphs[2]
        assertEquals("\u2022", parser.getListLabel(paragraph))
        assertEquals("bullet", parser.parseListInfo(paragraph)?.format)
    }
}