package com.tosin.docprocessor.data.parser.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListMarkerFormatterTest {

    @Test
    fun `parses ooxml and odf format names`() {
        assertEquals(ListMarkerFormatter.NumberFormat.DECIMAL, ListMarkerFormatter.parseFormat("decimal"))
        assertEquals(ListMarkerFormatter.NumberFormat.DECIMAL, ListMarkerFormatter.parseFormat(null))
        assertEquals(ListMarkerFormatter.NumberFormat.DECIMAL, ListMarkerFormatter.parseFormat(""))
        assertEquals(ListMarkerFormatter.NumberFormat.LOWER_LETTER, ListMarkerFormatter.parseFormat("lowerLetter"))
        assertEquals(ListMarkerFormatter.NumberFormat.UPPER_LETTER, ListMarkerFormatter.parseFormat("upperLetter"))
        assertEquals(ListMarkerFormatter.NumberFormat.LOWER_ROMAN, ListMarkerFormatter.parseFormat("lowerRoman"))
        assertEquals(ListMarkerFormatter.NumberFormat.UPPER_ROMAN, ListMarkerFormatter.parseFormat("upperRoman"))
        assertEquals(ListMarkerFormatter.NumberFormat.DECIMAL_ZERO, ListMarkerFormatter.parseFormat("decimalZero"))
        assertEquals(ListMarkerFormatter.NumberFormat.BULLET, ListMarkerFormatter.parseFormat("bullet"))
        assertEquals(ListMarkerFormatter.NumberFormat.NONE, ListMarkerFormatter.parseFormat("none"))
    }

    @Test
    fun `parses odf single-letter formats case-sensitively`() {
        assertEquals(ListMarkerFormatter.NumberFormat.LOWER_LETTER, ListMarkerFormatter.parseFormat("a"))
        assertEquals(ListMarkerFormatter.NumberFormat.UPPER_LETTER, ListMarkerFormatter.parseFormat("A"))
        assertEquals(ListMarkerFormatter.NumberFormat.LOWER_ROMAN, ListMarkerFormatter.parseFormat("i"))
        assertEquals(ListMarkerFormatter.NumberFormat.UPPER_ROMAN, ListMarkerFormatter.parseFormat("I"))
    }

    @Test
    fun `formats numbers in every style`() {
        assertEquals("3", ListMarkerFormatter.formatNumber(3, ListMarkerFormatter.NumberFormat.DECIMAL))
        assertEquals("03", ListMarkerFormatter.formatNumber(3, ListMarkerFormatter.NumberFormat.DECIMAL_ZERO))
        assertEquals("c", ListMarkerFormatter.formatNumber(3, ListMarkerFormatter.NumberFormat.LOWER_LETTER))
        assertEquals("C", ListMarkerFormatter.formatNumber(3, ListMarkerFormatter.NumberFormat.UPPER_LETTER))
        assertEquals("iii", ListMarkerFormatter.formatNumber(3, ListMarkerFormatter.NumberFormat.LOWER_ROMAN))
        assertEquals("IV", ListMarkerFormatter.formatNumber(4, ListMarkerFormatter.NumberFormat.UPPER_ROMAN))
        assertEquals("xiv", ListMarkerFormatter.formatNumber(14, ListMarkerFormatter.NumberFormat.LOWER_ROMAN))
        assertEquals("z", ListMarkerFormatter.formatNumber(26, ListMarkerFormatter.NumberFormat.LOWER_LETTER))
        assertEquals("aa", ListMarkerFormatter.formatNumber(27, ListMarkerFormatter.NumberFormat.LOWER_LETTER))
        assertEquals("", ListMarkerFormatter.formatNumber(0, ListMarkerFormatter.NumberFormat.UPPER_ROMAN))
    }

    @Test
    fun `substitutes the own-level placeholder in the template`() {
        val rendered = ListMarkerFormatter.render(
            template = "%1.",
            value = 3,
            format = ListMarkerFormatter.NumberFormat.DECIMAL,
            levelNumber = 1
        )
        assertEquals("3.", rendered)
    }

    @Test
    fun `leaves other-level placeholders verbatim`() {
        val rendered = ListMarkerFormatter.render(
            template = "%1.%2.",
            value = 2,
            format = ListMarkerFormatter.NumberFormat.DECIMAL,
            levelNumber = 2
        )
        assertEquals("%1.2.", rendered)
    }

    @Test
    fun `appends the number when the template has no own placeholder`() {
        val rendered = ListMarkerFormatter.render(
            template = ")",
            value = 5,
            format = ListMarkerFormatter.NumberFormat.DECIMAL,
            levelNumber = 1
        )
        assertEquals(")5", rendered)
    }

    @Test
    fun `renders odf prefix and suffix around the number`() {
        val rendered = ListMarkerFormatter.render(
            template = "(%1)",
            value = 1,
            format = ListMarkerFormatter.NumberFormat.LOWER_LETTER,
            levelNumber = 1
        )
        assertEquals("(a)", rendered)
    }

    @Test
    fun `none format renders no marker`() {
        assertNull(
            ListMarkerFormatter.render(
                template = "%1.",
                value = 1,
                format = ListMarkerFormatter.NumberFormat.NONE,
                levelNumber = 1
            )
        )
    }
}
