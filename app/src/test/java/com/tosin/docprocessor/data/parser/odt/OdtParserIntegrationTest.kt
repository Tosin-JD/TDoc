package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Round-trip test: build [DocumentElement]s → [OdtWriter] → parse the produced
 * ODT with [OdtXmlParser] and verify MVP fidelity survives.
 */
class OdtParserIntegrationTest {

    private val content: List<DocumentElement> = listOf(
        DocumentElement.SectionHeader(text = "Title", level = 1),
        DocumentElement.Paragraph(spans = listOf(TextSpan(text = "Hello"))),
        DocumentElement.Paragraph(
            spans = listOf(
                TextSpan(text = "Bold", isBold = true),
                TextSpan(text = " and plain")
            )
        ),
        DocumentElement.Paragraph(
            spans = listOf(TextSpan(text = "Visit")),
            hyperlink = HyperlinkInfo(address = "https://example.com")
        ),
        DocumentElement.Paragraph(
            spans = listOf(TextSpan(text = "Centered indented")),
            style = ParagraphStyle(
                alignment = ParagraphAlignment.CENTER,
                indentation = ParagraphIndentation(left = 283)
            )
        ),
        DocumentElement.Paragraph(
            spans = listOf(TextSpan(text = "Item one")),
            listLabel = "1.",
            listInfo = ListInfo(level = 0, format = "decimal", levelText = "1.")
        ),
        DocumentElement.Table(rows = listOf(listOf("a", "b"), listOf("c", "d"))),
        DocumentElement.HeaderFooter(
            HeaderFooterContent(
                kind = HeaderFooterKind.HEADER,
                variant = "default",
                text = "My Header",
                paragraphCount = 1,
                tableCount = 0
            )
        ),
        DocumentElement.Bookmark(
            com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo(
                id = "bm1",
                name = "bm1"
            )
        )
    )

    private val reparsed: List<DocumentElement> by lazy {
        val bytes = ByteArrayOutputStream()
        OdtWriter().write(bytes, content)
        val entries = OdtZipExtractor().extractAllEntries(bytes.toByteArray().inputStream())
        val cacheDir: File = Files.createTempDirectory("odt-roundtrip-test").toFile()
        OdtXmlParser(cacheDir, entries).parse(entries.getValue("content.xml"))
    }

    private fun paragraphs(): List<DocumentElement.Paragraph> =
        reparsed.filterIsInstance<DocumentElement.Paragraph>()

    @Test
    fun testHeadingRoundTrips() {
        val heading = reparsed.filterIsInstance<DocumentElement.SectionHeader>().first { it.text == "Title" }
        assertEquals(1, heading.level)
    }

    @Test
    fun testPlainParagraphRoundTrips() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Hello" } }
        assertEquals("Hello", paragraph.spans[0].text)
    }

    @Test
    fun testBoldSpanRoundTrips() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Bold" } }
        assertTrue(paragraph.spans.first { it.text == "Bold" }.isBold)
    }

    @Test
    fun testHyperlinkRoundTrips() {
        val paragraph = paragraphs().first { it.hyperlink != null }
        assertEquals("https://example.com", paragraph.hyperlink?.address)
    }

    @Test
    fun testParagraphStyleRoundTrips() {
        val paragraph = paragraphs().first { it.spans.any { s -> s.text == "Centered indented" } }
        assertEquals(ParagraphAlignment.CENTER, paragraph.style.alignment)
        val left = paragraph.style.indentation.left
        assertTrue("expected indentation left ~283, got $left", left != null && left in 275..290)
    }

    @Test
    fun testListRoundTrips() {
        val paragraph = paragraphs().first { it.listInfo != null }
        assertEquals("1.", paragraph.listLabel)
        assertEquals("1.", paragraph.listInfo?.levelText)
    }

    @Test
    fun testTableRoundTrips() {
        val table = reparsed.filterIsInstance<DocumentElement.Table>().first()
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), table.rows)
    }

    @Test
    fun testHeaderRoundTrips() {
        val header = reparsed.filterIsInstance<DocumentElement.HeaderFooter>().first()
        assertEquals(HeaderFooterKind.HEADER, header.content.kind)
        assertTrue(header.content.text.contains("My Header"))
    }

    @Test
    fun testBookmarkRoundTrips() {
        val bookmark = reparsed.filterIsInstance<DocumentElement.Bookmark>().first()
        assertEquals("bm1", bookmark.info.name)
    }

    @Test
    fun testDirectDrawImageParsedOutsideFrame() {
        val contentXml = OdtTestFixtures.contentXmlRoot(
            automaticStyles = "",
            bodyChildren = """
<text:p>Before</text:p>
<draw:image xlink:href="Pictures/pic.png" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"><svg:desc xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0">A direct image</svg:desc></draw:image>
<text:p>After</text:p>
"""
        )
        val zipBytes = OdtTestFixtures.odtZip(
            contentXml = contentXml,
            images = mapOf("pic.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        )
        val entries = OdtZipExtractor().extractAllEntries(zipBytes.inputStream())
        val cacheDir: File = Files.createTempDirectory("odt-direct-image").toFile()
        val elements = OdtXmlParser(cacheDir, entries).parse(entries.getValue("content.xml"))

        val image = elements.filterIsInstance<DocumentElement.Image>().firstOrNull()
            ?: throw AssertionError("direct draw:image should produce an Image element")
        assertEquals("A direct image", image.altText)
        val cached = File(cacheDir, image.sourceUri.substringAfterLast('/'))
        assertTrue("image bytes should be written to cache", cached.isFile)
    }
}