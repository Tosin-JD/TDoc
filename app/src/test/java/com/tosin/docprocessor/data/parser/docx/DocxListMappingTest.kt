package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.ListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxListMappingTest {

    private fun entries(documentXml: String, numberingXml: String? = null): Map<String, ByteArray> {
        val map = linkedMapOf(
            "[Content_Types].xml" to """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent().toByteArray(),
            "_rels/.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """.trimIndent().toByteArray(),
            "word/document.xml" to documentXml.toByteArray()
        )
        if (numberingXml != null) map["word/numbering.xml"] = numberingXml.toByteArray()
        return map
    }

    private val decimalNumbering = """
        <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:abstractNum w:abstractNumId="7">
            <w:lvl w:ilvl="0">
              <w:start w:val="1"/>
              <w:numFmt w:val="decimal"/>
              <w:lvlText w:val="%1."/>
            </w:lvl>
            <w:lvl w:ilvl="1">
              <w:start w:val="1"/>
              <w:numFmt w:val="lowerLetter"/>
              <w:lvlText w:val="%2."/>
            </w:lvl>
          </w:abstractNum>
          <w:num w:numId="42">
            <w:abstractNumId w:val="7"/>
          </w:num>
          <w:num w:numId="43">
            <w:abstractNumId w:val="7"/>
          </w:num>
        </w:numbering>
    """.trimIndent()

    private fun numberedParagraph(ilvl: Int, text: String, numId: Int = 42): String = """
        <w:p>
          <w:pPr><w:numPr><w:ilvl w:val="$ilvl"/><w:numId w:val="$numId"/></w:numPr></w:pPr>
          <w:r><w:t>$text</w:t></w:r>
        </w:p>
    """.trimIndent()

    @Test
    fun `ordered list markers are sequential not the start value`() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${numberedParagraph(0, "one")}
                ${numberedParagraph(0, "two")}
                ${numberedParagraph(0, "three")}
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(entries(xml, decimalNumbering)).parseEntries(entries(xml, decimalNumbering))

        val list = result.document.blocks.single() as BlockNode.ListBlock
        assertEquals(3, list.items.size)
        assertEquals("1.", list.items[0].marker?.text)
        assertEquals("2.", list.items[1].marker?.text)
        assertEquals("3.", list.items[2].marker?.text)
        assertEquals(ListKind.ORDERED, list.kind)
    }

    @Test
    fun `nested list levels become nested list blocks`() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${numberedParagraph(0, "top one")}
                ${numberedParagraph(1, "child a")}
                ${numberedParagraph(1, "child b")}
                ${numberedParagraph(0, "top two")}
                ${numberedParagraph(1, "child c")}
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(entries(xml, decimalNumbering)).parseEntries(entries(xml, decimalNumbering))

        val list = result.document.blocks.single() as BlockNode.ListBlock
        assertEquals(2, list.items.size)

        val firstItem = list.items[0]
        val nestedFirst = firstItem.blocks.filterIsInstance<BlockNode.ListBlock>().single()
        assertEquals(2, nestedFirst.items.size)
        assertEquals("a.", nestedFirst.items[0].marker?.text)
        assertEquals("b.", nestedFirst.items[1].marker?.text)
        assertEquals(1, nestedFirst.items[0].marker?.level)

        val secondNested = list.items[1].blocks.filterIsInstance<BlockNode.ListBlock>().single()
        assertEquals(1, secondNested.items.size)
        assertEquals("a.", secondNested.items[0].marker?.text)
    }

    @Test
    fun `a second list with the same numbering restarts at one`() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${numberedParagraph(0, "first list item")}
                <w:p><w:r><w:t>plain paragraph interrupts</w:t></w:r></w:p>
                ${numberedParagraph(0, "second list item", numId = 43)}
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(entries(xml, decimalNumbering)).parseEntries(entries(xml, decimalNumbering))

        val lists = result.document.blocks.filterIsInstance<BlockNode.ListBlock>()
        assertEquals(2, lists.size)
        assertEquals("1.", lists[0].items[0].marker?.text)
        // A fresh numId restarts numbering at the level's start value.
        assertEquals("1.", lists[1].items[0].marker?.text)
        assertTrue(lists.all { it.style.levels.isNotEmpty() })
    }

    @Test
    fun `lvl override start value drives sequential markers`() {
        val numbering = """
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="7">
                <w:lvl w:ilvl="0">
                  <w:start w:val="1"/>
                  <w:numFmt w:val="decimal"/>
                  <w:lvlText w:val="%1."/>
                </w:lvl>
              </w:abstractNum>
              <w:num w:numId="44">
                <w:abstractNumId w:val="7"/>
                <w:lvlOverride w:ilvl="0">
                  <w:startOverride w:val="5"/>
                </w:lvlOverride>
              </w:num>
            </w:numbering>
        """.trimIndent()
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${numberedParagraph(0, "five", numId = 44)}
                ${numberedParagraph(0, "six", numId = 44)}
              </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxDocumentParser(entries(xml, numbering)).parseEntries(entries(xml, numbering))

        val list = result.document.blocks.single() as BlockNode.ListBlock
        assertEquals(2, list.items.size)
        assertEquals("5.", list.items[0].marker?.text)
        assertEquals("6.", list.items[1].marker?.text)
    }
}
