package com.tosin.docprocessor.data.parser.util

import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** XML factory configuration for untrusted office-package parts. */
object SecureXml {
    fun parse(bytes: ByteArray): Document = newFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(bytes))

    fun newFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isIgnoringComments = false
        isCoalescing = true
        isExpandEntityReferences = false
        setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
        setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        setFeatureSafely("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        // Android's XMLConstants does not expose these JAXP constants on every
        // API level, so retain the standard property names as strings.
        setAttributeSafely("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttributeSafely("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        setXIncludeAwareSafely(false)
    }

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun DocumentBuilderFactory.setAttributeSafely(name: String, value: String) {
        runCatching { setAttribute(name, value) }
    }

    private fun DocumentBuilderFactory.setXIncludeAwareSafely(value: Boolean) {
        runCatching { isXIncludeAware = value }
    }
}
