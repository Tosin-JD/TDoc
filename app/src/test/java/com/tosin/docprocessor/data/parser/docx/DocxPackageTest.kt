package com.tosin.docprocessor.data.parser.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPackageTest {

    @Test
    fun `keeps external relationship targets external`() {
        val packageReader = DocxPackage.from(
            mapOf(
                "word/_rels/document.xml.rels" to """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="hyperlink" Target="https://example.test/a" TargetMode="External"/>
                      <Relationship Id="rId2" Type="image" Target="media/image1.png"/>
                    </Relationships>
                """.trimIndent().toByteArray()
            )
        )

        val relationships = packageReader.relationshipDetailsFor("word/document.xml")

        assertTrue(relationships.getValue("rId1").isExternal)
        assertEquals("https://example.test/a", relationships.getValue("rId1").target)
        assertFalse(relationships.getValue("rId2").isExternal)
        assertEquals("word/media/image1.png", relationships.getValue("rId2").target)
        assertEquals("word/media/image1.png", packageReader.relationshipsFor("word/document.xml").getValue("rId2"))
    }
}
