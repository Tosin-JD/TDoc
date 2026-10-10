package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.util.SecureXml
import org.w3c.dom.Element

class OdtMetadataParser {

    private val namespaces = mapOf(
        "meta" to "urn:oasis:names:tc:opendocument:xmlns:meta:1.0",
        "dc" to "http://purl.org/dc/elements/1.1/"
    )

    fun parse(metaXmlBytes: ByteArray?): DocumentElement.Metadata? {
        if (metaXmlBytes == null) return null

        return try {
            val doc = SecureXml.parse(metaXmlBytes)
            val root = doc.documentElement

            val attributes = mutableMapOf<String, String>()
            getElementText(root, "meta", "initial-creator")?.let { attributes["author"] = it }
            getElementText(root, "dc", "description")?.let { attributes["description"] = it }
            getElementText(root, "meta", "creation-date")?.let { attributes["createdAt"] = it }
            getElementText(root, "dc", "date")?.let { attributes["modifiedAt"] = it }
            getAttribute(root, "meta", "document-statistic", "word-count")?.let { attributes["wordCount"] = it }

            val info = MetadataInfo(
                kind = "ODT Metadata",
                title = getElementText(root, "dc", "title"),
                summary = "ODT document processed via TDoc",
                attributes = attributes
            )

            DocumentElement.Metadata(info = info)
        } catch (e: Exception) {
            null
        }
    }

    private fun getElementText(root: Element, prefix: String, localName: String, attrName: String? = null): String? {
        val ns = namespaces[prefix] ?: return null
        val nodes = root.getElementsByTagNameNS(ns, localName)
        if (nodes.length > 0) {
            val element = nodes.item(0) as Element
            return if (attrName != null) {
                element.getAttribute(attrName)
            } else {
                element.textContent?.trim()
            }
        }
        return null
    }

    private fun getAttribute(root: Element, prefix: String, localName: String, attributeName: String): String? {
        val ns = namespaces[prefix] ?: return null
        val element = root.getElementsByTagNameNS(ns, localName).item(0) as? Element ?: return null
        return element.getAttributeNS(ns, attributeName).takeIf { it.isNotBlank() }
    }
}
