package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.util.SecureXml
import org.junit.Assert.assertTrue
import org.junit.Test

class OdtStyleParserTest {

    @Test
    fun `content automatic styles override common styles`() {
        val common = SecureXml.parse(
            """<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"><style:style style:name="Emphasis"><style:text-properties fo:font-style="italic"/></style:style></office:document-styles>""".toByteArray()
        ).documentElement
        val automatic = SecureXml.parse(
            """<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"><office:automatic-styles><style:style style:name="Emphasis"><style:text-properties fo:font-weight="bold"/></style:style></office:automatic-styles></office:document-content>""".toByteArray()
        ).documentElement

        val styles = OdtStyleParser().parseStyles(listOf(common, automatic))

        assertTrue(styles.getValue("Emphasis").isBold)
    }
}
