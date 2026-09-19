package com.tosin.docprocessor.data.parser.docx

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class DocxParagraphParserTest {

    private val parser = DocxParagraphParser()

    @Test
    fun testParsePlainParagraph() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.createRun().setText("Hello world")
            val parsed = parser.parse(paragraph)
            assertEquals(listOf("Hello world"), parsed.spans.map { it.text })
            assertFalse(parsed.spans[0].isBold)
            assertEquals("000000", parsed.spans[0].color)
        }
    }

    @Test
    fun testParseStyledParagraph() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            val run = paragraph.createRun().apply {
                isBold = true
                isItalic = true
                setUnderline(UnderlinePatterns.SINGLE)
                fontSize = 14
                color = "FF0000"
                setText("Styled")
            }
            val parsed = parser.parse(paragraph)
            val span = parsed.spans.first { it.text == "Styled" }
            assertTrue(span.isBold)
            assertTrue(span.isItalic)
            assertTrue(span.isUnderline)
            assertEquals(14, span.fontSize)
            assertEquals("FF0000", span.color)
        }
    }

    @Test
    fun testHasOutlineFromParagraphOutlineLevel() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewPPr().addNewOutlineLvl().`val` = BigInteger.valueOf(1)
            paragraph.createRun().setText("Outlined")
            val span = parser.parse(paragraph).spans.first()
            assertTrue("outline level 1 should set hasOutline on spans", span.hasOutline)
        }
    }

    @Test
    fun testNoOutlineWhenOutlineLevelIsZero() {
        XWPFDocument().use { document ->
            val paragraph = document.createParagraph()
            paragraph.ctp.addNewPPr().addNewOutlineLvl().`val` = BigInteger.ZERO
            paragraph.createRun().setText("Not outlined")
            val span = parser.parse(paragraph).spans.first()
            assertFalse("outline level 0 should leave hasOutline false", span.hasOutline)
        }
    }
}