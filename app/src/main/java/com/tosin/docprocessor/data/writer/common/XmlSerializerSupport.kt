package com.tosin.docprocessor.data.writer.common

import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object XmlSerializerSupport {
    fun validate(xml: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's built-in JAXP implementation does not override
            // setXIncludeAware(), so calling it directly throws
            // UnsupportedOperationException on all Android API levels.
            // All security-hardening calls are therefore wrapped in
            // runCatching so that validation never crashes on a supported
            // but slightly restricted runtime.
            setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAwareSafely(false)
            runCatching { isExpandEntityReferences = false }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun DocumentBuilderFactory.setXIncludeAwareSafely(value: Boolean) {
        runCatching { isXIncludeAware = value }
    }
}
