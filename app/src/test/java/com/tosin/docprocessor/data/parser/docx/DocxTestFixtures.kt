package com.tosin.docprocessor.data.parser.docx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-crafted, deterministic DOCX fixtures for parser tests. Builds a minimal
 * OOXML ZIP in memory so tests stay on the JVM and never depend on external
 * binary tools.
 */
object DocxTestFixtures {

    fun simpleDocx(): ByteArray = docx(
        documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:body>
  <w:p><w:r><w:t>Hello world</w:t></w:r></w:p>
  <w:p>
   <w:r><w:rPr><w:b/><w:i/><w:u w:val="single"/></w:rPr><w:t>Styled</w:t></w:r>
  </w:p>
  <w:sectPr>
   <w:type w:val="nextPage"/>
   <w:pgSz w:w="12240" w:h="15840"/>
   <w:pgMar w:top="1440" w:right="1800" w:bottom="1440" w:left="1800" w:header="720" w:footer="720" w:gutter="0"/>
  </w:sectPr>
 </w:body>
</w:document>
""",
        withNumbering = false
    )

    fun listsDocx(): ByteArray = docx(
        documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
 <w:body>
  <w:p>
   <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>
   <w:r><w:t>Item zero</w:t></w:r>
  </w:p>
  <w:p>
   <w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr>
   <w:r><w:t>Item sub</w:t></w:r>
  </w:p>
  <w:p>
   <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr>
   <w:r><w:t>Bullet item</w:t></w:r>
  </w:p>
 </w:body>
</w:document>
""",
        withNumbering = true
    )

    fun corruptedDocx(): ByteArray = "this is definitely not a zip file".toByteArray()

    // ------------------------------------------------------------------

    private val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val pkgNs = "http://schemas.openxmlformats.org/package/2006/content-types"
    private val relNs = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun docx(documentXml: String, withNumbering: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes(withNumbering).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(rootRels().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(documentRels(withNumbering).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write(stylesXml().toByteArray())
            zip.closeEntry()
            if (withNumbering) {
                zip.putNextEntry(ZipEntry("word/numbering.xml"))
                zip.write(numberingXml().toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun contentTypes(withNumbering: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        append("""<Types xmlns="$pkgNs">""").append('\n')
        append(""" <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""").append('\n')
        append(""" <Default Extension="xml" ContentType="application/xml"/>""").append('\n')
        append(""" <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""").append('\n')
        append(""" <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""").append('\n')
        if (withNumbering) {
            append(""" <Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""").append('\n')
        }
        append("</Types>")
    }

    private fun rootRels(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="$relNs">
 <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"""

    private fun documentRels(withNumbering: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        append("""<Relationships xmlns="$relNs">""").append('\n')
        append(""" <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""").append('\n')
        if (withNumbering) {
            append(""" <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""").append('\n')
        }
        append("</Relationships>")
    }

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$wNs">
 <w:docDefaults>
  <w:rPrDefault><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault>
 </w:docDefaults>
</w:styles>
"""

    private fun numberingXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="$wNs">
 <w:abstractNum w:abstractNumId="1">
  <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="1."/></w:lvl>
  <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerRoman"/><w:lvlText w:val="i."/></w:lvl>
 </w:abstractNum>
 <w:abstractNum w:abstractNumId="2">
  <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/></w:lvl>
 </w:abstractNum>
 <w:num w:numId="1"><w:abstractNumId w:val="1"/></w:num>
 <w:num w:numId="2"><w:abstractNumId w:val="2"/></w:num>
</w:numbering>
"""
}