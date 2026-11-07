package com.tosin.docprocessor.data.writer.odt

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.DocumentMetadata
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.Resource
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.data.parser.odt.OdtDocumentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class OdtDocumentWriterTest {
    @Test
    fun writesValidOdtWithStoredFirstMimetypeAndManifestResources() = runBlocking {
        val output = ByteArrayOutputStream()
        OdtDocumentWriter().write(sampleDocument(), output)
        val entries = linkedMapOf<String, ByteArray>()
        var firstEntry: ZipEntry? = null
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (firstEntry == null) firstEntry = entry
                entries[entry.name] = zip.readBytes()
            }
        }

        assertEquals("mimetype", firstEntry?.name)
        assertEquals(ZipEntry.STORED, firstEntry?.method)
        val mimetype = OdtPackageConstants.MIME_TYPE.toByteArray(Charsets.UTF_8)
        assertTrue(mimetype.contentEquals(entries.getValue("mimetype")))
        assertEquals(CRC32().apply { update(mimetype) }.value, firstEntry?.crc)
        assertTrue(entries.keys.containsAll(setOf("content.xml", "styles.xml", "meta.xml", "META-INF/manifest.xml", "Pictures/image1.png")))
        entries.filterKeys { it.endsWith(".xml") }.forEach { (_, xml) -> parseXml(xml) }

        val content = parseXml(entries.getValue("content.xml"))
        assertNotNull(content.getElementsByTagNameNS(OdtPackageConstants.TEXT_NS, "h").item(0))
        assertNotNull(content.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table").item(0))
        assertNotNull(content.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "image").item(0))
        assertTrue(entries.getValue("content.xml").toString(Charsets.UTF_8).contains("\uFFFD"))
        val manifest = entries.getValue("META-INF/manifest.xml").toString(Charsets.UTF_8)
        assertTrue(manifest.contains("Pictures/image1.png"))
        assertTrue(manifest.contains("image/png"))
        val roundTripped = OdtDocumentParser(entries).parseEntries(entries)
        assertTrue(roundTripped.document.blocks.isNotEmpty())
    }

    private fun sampleDocument() = ParsedDocument(
        blocks = listOf(
            BlockNode.Heading(listOf(InlineNode.Text("H & title")), level = 1),
            BlockNode.Paragraph(listOf(InlineNode.Text("leading  space \u0002", style = com.tosin.docprocessor.domain.document.model.style.TextStyle(italic = true)))),
            BlockNode.ListBlock(
                kind = ListKind.UNORDERED,
                style = ListStyle(kind = ListKind.UNORDERED),
                items = listOf(BlockNode.ListItem(listOf(BlockNode.Paragraph(listOf(InlineNode.Text("bullet"))))))
            ),
            BlockNode.Table(rows = listOf(BlockNode.TableRow(
                cells = listOf(BlockNode.TableCell(listOf(BlockNode.Paragraph(listOf(InlineNode.Text("cell"))))))
            ))),
            BlockNode.Figure(resourceId = "img.png", altText = "figure")
        ),
        metadata = DocumentMetadata(title = "A title & more", creator = "Author"),
        resources = mapOf(
            "img.png" to Resource(
                id = "img.png",
                logicalUri = "img.png",
                mimeType = "image/png",
                contentHash = "image",
                bytes = byteArrayOf(4, 5, 6)
            )
        )
    )

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
}
