package com.tosin.docprocessor.data.parser.odt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdtMetadataParserTest {

    private val parser = OdtMetadataParser()

    private val completeMetaXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 office:version="1.2">
 <office:meta>
  <meta:initial-creator>Ada Lovelace</meta:initial-creator>
  <dc:creator>Charles Babbage</dc:creator>
  <dc:title>Analytical Engine Notes</dc:title>
  <dc:description>Notes on the engine</dc:description>
  <dc:subject>Computing</dc:subject>
  <dc:format>application/vnd.oasis.opendocument.text</dc:format>
  <dc:type>text</dc:type>
  <meta:creation-date>2026-09-18T10:00:00</meta:creation-date>
  <dc:date>2026-09-19T09:30:00</dc:date>
  <meta:editing-cycles>7</meta:editing-cycles>
  <meta:editing-duration>PT2H30M</meta:editing-duration>
  <meta:generator>LibreOffice/7.6</meta:generator>
  <meta:keyword>history</meta:keyword>
  <meta:keyword>computing</meta:keyword>
  <meta:document-statistic meta:word-count="1200" meta:character-count="7500"
     meta:paragraph-count="40" meta:table-count="2" meta:image-count="3"
     meta:page-count="8" meta:cell-count="12" meta:object-count="1"/>
  <meta:user-defined meta:name="Approved">true</meta:user-defined>
  <meta:user-defined meta:name="Reviewer">Babbage</meta:user-defined>
 </office:meta>
</office:document-meta>
"""

    @Test
    fun testParseAllMetadata() {
        val metadata = parser.parse(completeMetaXml.toByteArray())
            ?: throw AssertionError("Expected metadata, got null")

        assertEquals("ODT Metadata", metadata.info.kind)
        assertEquals("Analytical Engine Notes", metadata.info.title)
        assertEquals("Ada Lovelace", metadata.info.attributes["author"])
        assertEquals("Charles Babbage", metadata.info.attributes["dcCreator"])
        assertEquals("2026-09-18T10:00:00", metadata.info.attributes["createdAt"])
        assertEquals("2026-09-19T09:30:00", metadata.info.attributes["modifiedAt"])
        assertEquals("7", metadata.info.attributes["editingCycles"])
        assertEquals("PT2H30M", metadata.info.attributes["editingDuration"])
        assertEquals("Computing", metadata.info.attributes["subject"])
        assertEquals("text", metadata.info.attributes["type"])
    }

    @Test
    fun testParseDocumentStatistic() {
        val metadata = parser.parse(completeMetaXml.toByteArray())
            ?: throw AssertionError("Expected metadata, got null")

        val attributes = metadata.info.attributes
        assertEquals("1200", attributes["wordCount"])
        assertEquals("7500", attributes["characterCount"])
        assertEquals("40", attributes["paragraphCount"])
        assertEquals("2", attributes["tableCount"])
        assertEquals("3", attributes["imageCount"])
        assertEquals("8", attributes["pageCount"])
    }

    @Test
    fun testParseUserDefinedProperties() {
        val metadata = parser.parse(completeMetaXml.toByteArray())
            ?: throw AssertionError("Expected metadata, got null")

        val userDefined = metadata.info.children
        assertEquals(2, userDefined.size)
        assertEquals("Approved", userDefined[0].title)
        assertEquals("true", userDefined[0].attributes["value"])
        assertEquals("Reviewer", userDefined[1].title)
        assertEquals("Babbage", userDefined[1].attributes["value"])
    }

    @Test
    fun testParseMissingMetadata() {
        val metaXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
 xmlns:dc="http://purl.org/dc/elements/1.1/" office:version="1.2">
 <office:meta>
  <dc:title>Bare</dc:title>
 </office:meta>
</office:document-meta>
"""
        val metadata = parser.parse(metaXml.toByteArray())
            ?: throw AssertionError("Expected metadata, got null")

        assertEquals("Bare", metadata.info.title)
        assertTrue("missing author should not be set", !metadata.info.attributes.containsKey("author"))
        assertNull(metadata.info.attributes["wordCount"])
    }

    @Test
    fun testParseNullReturnsNull() {
        assertNull(parser.parse(null))
    }

    @Test
    fun testParseInvalidXmlReturnsNull() {
        val result = parser.parse("<not-well-formed>".toByteArray())
        assertNull(result)
    }

    @Test
    fun testParseEmptyMetaXmlReturnsMetadata() {
        val metaXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
 xmlns:dc="http://purl.org/dc/elements/1.1/" office:version="1.2">
 <office:meta/>
</office:document-meta>
"""
        val metadata = parser.parse(metaXml.toByteArray())
            ?: throw AssertionError("Empty meta.xml should still produce Metadata")
        assertEquals("ODT Metadata", metadata.info.kind)
    }
}