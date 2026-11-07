package com.tosin.docprocessor.data.writer.common

import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object XmlSerializerSupport {
    fun validate(xml: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's built-in parser does not consistently expose every JAXP
            // security feature. These flags harden validation, but must not make
            // otherwise valid document exports fail on supported Android versions.
            setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }
}
