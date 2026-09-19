package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdtStyleParserTest {

    private val stylesXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
 xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
 office:version="1.2">
 <office:styles>
  <style:default-style style:family="paragraph">
   <style:paragraph-properties fo:text-align="start"/>
   <style:text-properties fo:font-size="11pt" fo:color="#000000"/>
  </style:default-style>
  <style:style style:name="Base" style:family="paragraph">
   <style:text-properties fo:font-weight="bold" fo:color="#FF0000"/>
  </style:style>
  <style:style style:name="Child" style:family="paragraph" style:parent-style-name="Base">
   <style:text-properties fo:font-style="italic"/>
   <style:paragraph-properties fo:text-align="center" fo:margin-left="1cm"/>
  </style:style>
  <style:style style:name="Heading1" style:family="paragraph" style:parent-style-name="Base">
   <style:paragraph-properties fo:text-align="start" style:outline-level="1"/>
  </style:style>
 </office:styles>
</office:document-styles>
"""

    private fun parse(): Map<String, OdtStyleParser.StyleProperties> =
        OdtStyleParser().parseStyles(OdtTestFixtures.parseDocument(stylesXml))

    @Test
    fun testParseTextProperties() {
        val styles = parse()
        val base = styles["Base"] ?: throw AssertionError("Base style missing")
        assertTrue(base.textProperties?.isBold == true)
        assertEquals("FF0000", base.textProperties?.color)
    }

    @Test
    fun testParseParagraphProperties() {
        val styles = parse()
        val child = styles["Child"] ?: throw AssertionError("Child style missing")
        assertEquals(ParagraphAlignment.CENTER, child.paragraphProperties?.alignment)
        assertEquals(566, child.paragraphProperties?.indentation?.left)
    }

    @Test
    fun testParseHeadingStyle() {
        val styles = parse()
        val heading = styles["Heading1"] ?: throw AssertionError("Heading1 style missing")
        assertTrue(heading.isHeading)
        assertEquals(1, heading.headingLevel)
    }

    @Test
    fun testStyleInheritance() {
        val styles = parse()
        val child = styles["Child"] ?: throw AssertionError("Child style missing")
        assertTrue("inherited bold from parent", child.textProperties?.isBold == true)
        assertTrue("own italic", child.textProperties?.isItalic == true)
        assertEquals("inherited color from parent", "FF0000", child.textProperties?.color)
        assertEquals("parent default font size", 11f, child.textProperties?.fontSize)
    }

    @Test
    fun testDefaultStyleApplied() {
        val styles = parse()
        val base = styles["Base"] ?: throw AssertionError("Base style missing")
        assertEquals("font size from default style", 11f, base.textProperties?.fontSize)
        assertEquals(ParagraphAlignment.START, base.paragraphProperties?.alignment)
    }

    @Test
    fun testNoStyleReturnsNull() {
        val styles = parse()
        assertFalse(styles.containsKey("MissingStyle"))
        assertNull(styles["MissingStyle"])
    }
}