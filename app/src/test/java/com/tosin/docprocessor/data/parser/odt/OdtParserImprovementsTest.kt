package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

class OdtParserImprovementsTest {

    @Test
    fun `paragraph parser falls back to default styling for missing styles`() {
        val parser = OdtParagraphParser(emptyMap())
        val paragraph = xmlElement(
            """
            <text:p xmlns:text="${OdtNamespaces.TEXT}">
              <text:span text:style-name="MissingStyle">Styled text</text:span>
            </text:p>
            """.trimIndent()
        )

        val elements = parser.parseParagraph(paragraph)

        val parsedParagraph = elements.single() as DocumentElement.Paragraph
        val span = parsedParagraph.spans.single()
        assertEquals("Styled text", span.text)
        assertFalse(span.isBold)
        assertFalse(span.isItalic)
        assertFalse(span.isUnderline)
        assertEquals("000000", span.color)
        assertEquals(12, span.fontSize)
    }

    @Test
    fun `paragraph parser captures hyperlink at paragraph level`() {
        val parser = OdtParagraphParser(emptyMap())
        val paragraph = xmlElement(
            """
            <text:p xmlns:text="${OdtNamespaces.TEXT}" xmlns:xlink="${OdtNamespaces.XLINK}">
              <text:a xlink:href="https://example.com">Visit example</text:a>
            </text:p>
            """.trimIndent()
        )

        val elements = parser.parseParagraph(paragraph)

        val parsedParagraph = elements.single() as DocumentElement.Paragraph
        assertEquals("https://example.com", parsedParagraph.hyperlink?.address)
        assertEquals("Visit example", parsedParagraph.spans.single().text)
    }

    @Test
    fun `image parser returns placeholder when embedded bytes are missing`() {
        val parser = OdtImageParser(createTempDir(), emptyMap())
        val frame = xmlElement(
            """
            <draw:frame xmlns:draw="${OdtNamespaces.DRAW}" xmlns:xlink="${OdtNamespaces.XLINK}">
              <draw:image xlink:href="Pictures/missing.png"/>
            </draw:frame>
            """.trimIndent()
        )

        val image = parser.parseImage(frame)

        assertNotNull(image)
        image as DocumentElement.Image
        assertEquals("missing:Pictures/missing.png", image.sourceUri)
        assertEquals("Missing embedded image: missing.png", image.caption)
    }

    @Test
    fun `image parser reuses deterministic cache file for duplicate bytes`() {
        val cacheDir = createTempDir()
        val bytes = "same-image".toByteArray()
        val zipEntries = mapOf(
            "Pictures/a.png" to bytes,
            "Pictures/b.png" to bytes
        )
        val parser = OdtImageParser(cacheDir, zipEntries)

        val first = parser.parseImage(
            xmlElement(
                """
                <draw:frame xmlns:draw="${OdtNamespaces.DRAW}" xmlns:xlink="${OdtNamespaces.XLINK}">
                  <draw:image xlink:href="Pictures/a.png"/>
                </draw:frame>
                """.trimIndent()
            )
        ) as DocumentElement.Image
        val second = parser.parseImage(
            xmlElement(
                """
                <draw:frame xmlns:draw="${OdtNamespaces.DRAW}" xmlns:xlink="${OdtNamespaces.XLINK}">
                  <draw:image xlink:href="Pictures/b.png"/>
                </draw:frame>
                """.trimIndent()
            )
        ) as DocumentElement.Image

        assertEquals(first.sourceUri, second.sourceUri)
        assertTrue(File(first.sourceUri).exists())
        assertEquals(1, cacheDir.listFiles()?.size)
    }

    private fun xmlElement(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement
    }

    private fun createTempDir(): File =
        Files.createTempDirectory("tdoc-odt-").toFile().apply { deleteOnExit() }
}
