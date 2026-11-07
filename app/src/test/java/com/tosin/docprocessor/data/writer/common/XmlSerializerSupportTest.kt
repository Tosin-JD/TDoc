package com.tosin.docprocessor.data.writer.common

import org.junit.Assert.assertThrows
import org.junit.Test

class XmlSerializerSupportTest {

    @Test
    fun `validate accepts well-formed XML without throwing`() {
        // Should not throw any exception (including UnsupportedOperationException from Android JAXP)
        XmlSerializerSupport.validate(
            """<?xml version="1.0" encoding="UTF-8"?><root xmlns="urn:test"><child>text</child></root>"""
        )
    }

    @Test
    fun `validate accepts minimal DOCX document part`() {
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>Hello</w:t></w:r></w:p><w:sectPr/></w:body>
            </w:document>"""
        XmlSerializerSupport.validate(docXml)
    }

    @Test
    fun `validate accepts minimal ODT content part`() {
        val contentXml = """<?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:body><office:text><text:p>Hello</text:p></office:text></office:body>
            </office:document-content>"""
        XmlSerializerSupport.validate(contentXml)
    }

    @Test
    fun `validate rejects malformed XML`() {
        assertThrows(Exception::class.java) {
            XmlSerializerSupport.validate("<unclosed>")
        }
    }
}

