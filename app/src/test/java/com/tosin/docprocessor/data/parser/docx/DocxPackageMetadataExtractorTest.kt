package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxPackageMetadataExtractorTest {

    private val extractor = DocxPackageMetadataExtractor()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private val coreXml = """<?xml version="1.0" encoding="UTF-8"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <dc:title>Quarterly Report</dc:title>
 <dc:subject>Finance</dc:subject>
 <dc:creator>Ada Lovelace</dc:creator>
 <cp:lastModifiedBy>Charles Babbage</cp:lastModifiedBy>
 <cp:revision>3</cp:revision>
 <dcterms:created xsi:type="dcterms:W3CDTF">2026-09-01T08:00:00Z</dcterms:created>
</cp:coreProperties>
"""

    private val appXml = """<?xml version="1.0" encoding="UTF-8"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
 <Pages>4</Pages>
 <Words>1200</Words>
 <Characters>7500</Characters>
 <Lines>90</Lines>
 <Paragraphs>40</Paragraphs>
 <Company>Acme Inc</Company>
</Properties>
"""

    private val customXml = """<?xml version="1.0" encoding="UTF-8"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
 <property fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}" pid="2" name="Reviewed">
  <vt:lpwstr>true</vt:lpwstr>
 </property>
</Properties>
"""

    private val settingsXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:documentProtection w:edit="comments" w:enforcement="1"/>
 <w:trackRevisions/>
 <w:hyphenationZone w:val="425"/>
</w:settings>
"""

    private val fontTableXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:font w:name="Calibri">
  <w:family w:val="swiss"/>
 </w:font>
 <w:font w:name="Times New Roman">
  <w:altName w:val="Times New Roman"/>
  <w:family w:val="roman"/>
 </w:font>
</w:fonts>
"""

    private val stylesXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:style w:type="paragraph" w:styleId="Heading1">
  <w:name w:val="heading 1"/>
  <w:basedOn w:val="Normal"/>
 </w:style>
</w:styles>
"""

    private val documentXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:background w:color="FFFF00"/>
</w:document>
"""

    private val packageWithAllParts = zipOf(
        "docProps/core.xml" to coreXml,
        "docProps/app.xml" to appXml,
        "docProps/custom.xml" to customXml,
        "word/settings.xml" to settingsXml,
        "word/fontTable.xml" to fontTableXml,
        "word/styles.xml" to stylesXml,
        "word/document.xml" to documentXml
    )

    private fun metadataOf(kind: String): DocumentElement.Metadata? =
        extractor.extract(XWPFDocument(), DocxPackage.from(packageWithAllParts))
            .filterIsInstance<DocumentElement.Metadata>()
            .firstOrNull { it.info.kind == kind }

    @Test
    fun testParseCoreProperties() {
        val core = metadataOf("document-properties-core")
            ?: throw AssertionError("expected core properties metadata")
        assertEquals("Quarterly Report", core.info.attributes["title"])
        assertEquals("Ada Lovelace", core.info.attributes["author"])
        assertEquals("Charles Babbage", core.info.attributes["lastModifiedBy"])
        assertEquals("3", core.info.attributes["revision"])
        assertEquals("Finance", core.info.attributes["subject"])
    }

    @Test
    fun testParseExtendedProperties() {
        val extended = metadataOf("document-properties-extended")
            ?: throw AssertionError("expected extended properties metadata")
        assertEquals("4", extended.info.attributes["pages"])
        assertEquals("1200", extended.info.attributes["words"])
        assertEquals("7500", extended.info.attributes["characters"])
        assertEquals("Acme Inc", extended.info.attributes["company"])
    }

    @Test
    fun testParseCustomProperties() {
        val custom = metadataOf("document-properties-custom")
            ?: throw AssertionError("expected custom properties metadata")
        assertEquals(1, custom.info.children.size)
        assertEquals("Reviewed", custom.info.children[0].title)
        assertEquals("true", custom.info.children[0].attributes["value"])
    }

    @Test
    fun testParseSettings() {
        val settings = metadataOf("document-settings")
            ?: throw AssertionError("expected settings metadata")
        assertEquals("comments", settings.info.attributes["documentProtection.edit"])
        assertEquals("1", settings.info.attributes["documentProtection.enforcement"])
        assertEquals("true", settings.info.attributes["trackRevisions"])
        assertEquals("425", settings.info.attributes["hyphenationZone"])
    }

    @Test
    fun testParseFonts() {
        val fonts = metadataOf("embedded-fonts")
            ?: throw AssertionError("expected embedded fonts metadata")
        assertEquals(2, fonts.info.children.size)
        assertEquals("Calibri", fonts.info.children[0].title)
        assertEquals("swiss", fonts.info.children[0].attributes["family"])
    }

    @Test
    fun testParseStyles() {
        val styles = metadataOf("styles")
            ?: throw AssertionError("expected styles metadata")
        assertEquals(1, styles.info.children.size)
        val heading = styles.info.children[0]
        assertEquals("heading 1", heading.title)
        assertEquals("paragraph", heading.attributes["type"])
        assertEquals("Heading1", heading.attributes["styleId"])
    }

    @Test
    fun testParseBackground() {
        val background = metadataOf("background")
            ?: throw AssertionError("expected background metadata")
        assertEquals("FFFF00", background.info.attributes["color"])
    }

    @Test
    fun testEmptyPackageProducesNoMetadata() {
        val results = extractor.extract(XWPFDocument(), DocxPackage.from(zipOf()))
        assertTrue(results.isEmpty())
    }
}