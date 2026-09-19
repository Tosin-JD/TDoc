package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OdtParagraphParserTest {

    private val automaticStyles = """
<style:style style:name="P1" style:family="paragraph">
 <style:paragraph-properties fo:text-align="start"/>
</style:style>
<style:style style:name="P-right" style:family="paragraph">
 <style:paragraph-properties fo:text-align="end"/>
</style:style>
<style:style style:name="P-indent" style:family="paragraph">
 <style:paragraph-properties fo:text-align="left" fo:margin-left="1cm"/>
</style:style>
<style:style style:name="Heading1" style:family="paragraph">
 <style:paragraph-properties fo:text-align="left" style:outline-level="1"/>
</style:style>
<style:style style:name="BoldSpan" style:family="text">
 <style:text-properties fo:font-weight="bold"/>
</style:style>
<style:style style:name="RedSpan" style:family="text">
 <style:text-properties fo:color="#FF0000"/>
</style:style>
"""

    private val contentXml = OdtTestFixtures.contentXmlRoot(
        automaticStyles = automaticStyles,
        bodyChildren = """
<text:p text:style-name="P1">Plain</text:p>
<text:p text:style-name="P1"><text:span text:style-name="BoldSpan">Bold</text:span> and <text:span text:style-name="RedSpan">Red</text:span></text:p>
<text:p text:style-name="P-right">Right</text:p>
<text:p text:style-name="P-indent">Indented</text:p>
<text:h text:outline-level="1" text:style-name="Heading1">Title</text:h>
<text:p text:style-name="P1"><text:a xlink:href="https://example.com" xlink:type="simple" xlink:show="replace" xlink:actuate="onRequest">Visit</text:a></text:p>
"""
    )

    private val elements: List<DocumentElement> by lazy {
        val zipBytes = OdtTestFixtures.odtZip(contentXml = contentXml)
        val entries = OdtZipExtractor().extractAllEntries(zipBytes.inputStream())
        val cacheDir: File = Files.createTempDirectory("odt-para-test").toFile()
        OdtXmlParser(cacheDir, entries).parse(entries.getValue("content.xml"))
    }

    private fun paragraphs(): List<DocumentElement.Paragraph> =
        elements.filterIsInstance<DocumentElement.Paragraph>()

    @Test
    fun testParsePlainParagraph() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Plain" } }
        assertEquals(1, paragraph.spans.size)
        assertEquals("Plain", paragraph.spans[0].text)
        assertEquals(false, paragraph.spans[0].isBold)
        assertEquals("000000", paragraph.spans[0].color)
    }

    @Test
    fun testParseMixedFormatting() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Bold" } }
        val bold = paragraph.spans.first { it.text == "Bold" }
        assertEquals(true, bold.isBold)
        val red = paragraph.spans.first { it.text == "Red" }
        assertEquals("FF0000", red.color)
    }

    @Test
    fun testParseCenteredParagraph() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Right" } }
        assertEquals(ParagraphAlignment.END, paragraph.style.alignment)
    }

    @Test
    fun testParseIndentedParagraph() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Indented" } }
        assertTrue((paragraph.style.indentation.left ?: 0) > 0)
        assertEquals(566, paragraph.style.indentation.left)
    }

    @Test
    fun testParseHeadingParagraph() {
        val heading = elements.filterIsInstance<DocumentElement.SectionHeader>().first { it.text == "Title" }
        assertEquals(1, heading.level)
    }

    @Test
    fun testParseHyperlink() {
        val paragraph = paragraphs().first { it.hyperlink != null }
        assertEquals("https://example.com", paragraph.hyperlink?.address)
        assertEquals("Visit", paragraph.spans.first { it.text == "Visit" }.text)
    }
}