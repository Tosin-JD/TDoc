package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import org.w3c.dom.Element
import com.tosin.docprocessor.data.parser.util.TDocLogger
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class OdtMetadataParser {

    private val namespaces = mapOf(
        "meta" to OdtNamespaces.META,
        "dc" to OdtNamespaces.DC
    )

    fun parse(metaXmlBytes: ByteArray?): DocumentElement.Metadata? {
        if (metaXmlBytes == null) return null

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(metaXmlBytes))
            val root = doc.documentElement

            val attributes = mutableMapOf<String, String>()
            getElementText(root, "meta", "initial-creator")?.let { attributes["author"] = it }
            getElementText(root, "dc", "description")?.let { attributes["description"] = it }
            getElementText(root, "meta", "creation-date")?.let { attributes["createdAt"] = it }
            getElementText(root, "dc", "date")?.let { attributes["modifiedAt"] = it }
            getElementText(root, "meta", "document-statistic", "meta:word-count")?.let { attributes["wordCount"] = it }

            val info = MetadataInfo(
                kind = "ODT Metadata",
                title = getElementText(root, "dc", "title"),
                summary = "ODT document processed via TDoc",
                attributes = attributes
            )

            DocumentElement.Metadata(info = info)
        } catch (e: Exception) {
            TDocLogger.warn("Failed to parse ODT metadata, continuing without metadata", e)
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
}
