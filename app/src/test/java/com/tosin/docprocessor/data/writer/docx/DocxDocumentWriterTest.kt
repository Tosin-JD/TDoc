package com.tosin.docprocessor.data.writer.docx

import com.tosin.docprocessor.data.writer.common.PackageEntry
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.Resource
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.data.parser.docx.DocxDocumentParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class DocxDocumentWriterTest {
    @Test
    fun writesValidPackageAndPreservesStreamOwnership() = runBlocking {
        val output = TrackingOutputStream()
        DocxDocumentWriter().write(sampleDocument(), output)
        assertFalse(output.closed)

        val entries = unzip(output.toByteArray())
        assertTrue(entries.keys.containsAll(setOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/document.xml",
            "word/styles.xml",
            "word/_rels/document.xml.rels",
            "word/numbering.xml",
            "word/media/image1.png"
        )))
        entries.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (_, bytes) ->
            parseXml(bytes)
        }

        val document = parseXml(entries.getValue("word/document.xml"))
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        assertNotNull(document.getElementsByTagNameNS(w, "tbl").item(0))
        assertNotNull(document.getElementsByTagNameNS(w, "numPr").item(0))
        assertTrue(entries.getValue("word/document.xml").toString(Charsets.UTF_8).contains("\uFFFD"))

        val relationships = parseXml(entries.getValue("word/_rels/document.xml.rels"))
        val rels = relationships.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/package/2006/relationships",
            "Relationship"
        )
        for (index in 0 until rels.length) {
            val relationship = rels.item(index) as Element
            val target = relationship.getAttribute("Target")
            if (!target.startsWith("../")) assertTrue(entries.containsKey("word/$target"))
            else assertTrue(entries.containsKey(target.removePrefix("../")))
        }
        val rootRelationships = parseXml(entries.getValue("_rels/.rels"))
        val rootRels = rootRelationships.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/package/2006/relationships",
            "Relationship"
        )
        for (index in 0 until rootRels.length) {
            val target = (rootRels.item(index) as Element).getAttribute("Target")
            assertTrue(entries.containsKey(target))
        }
        val roundTripped = DocxDocumentParser(entries).parseEntries(entries)
        assertTrue(roundTripped.document.blocks.isNotEmpty())
    }

    private fun sampleDocument() = ParsedDocument(
        blocks = listOf(
            BlockNode.Heading(listOf(InlineNode.Text("Heading")), level = 1),
            BlockNode.Paragraph(listOf(InlineNode.Text("  A <tag> & emoji \uD83D\uDE03\u0001", TextStyle(bold = true)))),
            BlockNode.ListBlock(
                kind = ListKind.ORDERED,
                style = ListStyle(kind = ListKind.ORDERED),
                items = listOf(BlockNode.ListItem(listOf(BlockNode.Paragraph(listOf(InlineNode.Text("item"))))))
            ),
            BlockNode.Table(rows = listOf(BlockNode.TableRow(
                cells = listOf(BlockNode.TableCell(listOf(BlockNode.Paragraph(listOf(InlineNode.Text("cell"))))))
            ))),
            BlockNode.Figure(resourceId = "media/photo.png", altText = "photo")
        ),
        resources = mapOf(
            "media/photo.png" to Resource(
                id = "media/photo.png",
                logicalUri = "media/photo.png",
                mimeType = "image/png",
                contentHash = "test",
                bytes = byteArrayOf(1, 2, 3)
            )
        )
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private class TrackingOutputStream : FilterOutputStream(ByteArrayOutputStream()) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }

        fun toByteArray(): ByteArray = (out as ByteArrayOutputStream).toByteArray()
    }
}
