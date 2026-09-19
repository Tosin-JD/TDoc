package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the ODT `meta.xml` package part into a single [DocumentElement.Metadata].
 *
 * Reads all standard ODF metadata: DC elements (`dc:title`, `dc:description`,
 * `dc:creator`, `dc:subject`, `dc:date`, `dc:format`, `dc:type`), meta elements
 * (`meta:initial-creator`, `meta:creation-date`, `meta:editing-cycles`,
 * `meta:editing-duration`, `meta:document-statistic`), and user-defined
 * properties (`meta:user-defined`). Any error (including a malformed part)
 * degrades gracefully to `null`.
 */
class OdtMetadataParser {

    private val namespaces = mapOf(
        "meta" to META_NS,
        "dc" to DC_NS,
        "office" to OFFICE_NS
    )

    fun parse(metaXmlBytes: ByteArray?): DocumentElement.Metadata? {
        if (metaXmlBytes == null) return null

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(metaXmlBytes))
            val root = doc.documentElement

            val attributes = linkedMapOf<String, String>()
            getElementText(root, "meta", "initial-creator")?.let { attributes["author"] = it }
            getElementText(root, "meta", "creation-date")?.let { attributes["createdAt"] = it }
            getElementText(root, "dc", "date")?.let { attributes["modifiedAt"] = it }
            getElementText(root, "dc", "description")?.let { attributes["description"] = it }
            getElementText(root, "dc", "creator")?.let { attributes["dcCreator"] = it }
            getElementText(root, "dc", "subject")?.let { attributes["subject"] = it }
            getElementText(root, "dc", "format")?.let { attributes["format"] = it }
            getElementText(root, "dc", "type")?.let { attributes["type"] = it }
            getElementText(root, "meta", "editing-cycles")?.let { attributes["editingCycles"] = it }
            getElementText(root, "meta", "editing-duration")?.let { attributes["editingDuration"] = it }
            getElementText(root, "meta", "generator")?.let { attributes["generator"] = it }
            getElementText(root, "meta", "keyword")?.let { attributes["keywords"] = it }

            // Document statistics: a single <meta:document-statistic> element
            // with one attribute per metric.
            parseDocumentStatistic(root)?.let { attributes.putAll(it) }

            // User-defined properties become children entries like custom props.
            val children = parseUserDefined(root)

            val info = MetadataInfo(
                kind = "ODT Metadata",
                title = getElementText(root, "dc", "title"),
                summary = buildString {
                    append("ODT document processed via TDoc")
                    attributes["author"]?.let {
                        append(", author=")
                        append(it)
                    }
                    attributes["wordCount"]?.let {
                        append(", words=")
                        append(it)
                    }
                },
                attributes = attributes,
                children = children
            )

            DocumentElement.Metadata(info)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDocumentStatistic(root: Element): Map<String, String>? {
        val node = getElement(root, "meta", "document-statistic") ?: return null
        val result = linkedMapOf<String, String>()
        val metrics = mapOf(
            "word-count" to "wordCount",
            "character-count" to "characterCount",
            "non-whitespace-character-count" to "nonWhitespaceCharacterCount",
            "paragraph-count" to "paragraphCount",
            "table-count" to "tableCount",
            "image-count" to "imageCount",
            "object-count" to "objectCount",
            "page-count" to "pageCount",
            "cell-count" to "cellCount",
            "frame-count" to "frameCount",
            "drawing-count" to "drawingCount",
            "sentence-count" to "sentenceCount",
            "syllable-count" to "syllableCount"
        )
        metrics.forEach { (attrName, label) ->
            node.getAttributeNS(META_NS, attrName)
                .takeIf { it.isNotBlank() }
                ?.let { result[label] = it }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parseUserDefined(root: Element): List<MetadataInfo> {
        val nodes = root.getElementsByTagNameNS(META_NS, "user-defined")
        val children = mutableListOf<MetadataInfo>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttributeNS(META_NS, "name")
                .takeIf { it.isNotBlank() }
                ?: "custom-${i + 1}"
            children += MetadataInfo(
                kind = "user-defined",
                title = name,
                summary = "$name=${element.textContent?.trim().orEmpty()}",
                attributes = mapOf(
                    "name" to name,
                    "value" to (element.textContent?.trim().orEmpty())
                ),
                source = "meta.xml"
            )
        }
        return children
    }

    private fun getElementText(root: Element, prefix: String, localName: String): String? =
        getElement(root, prefix, localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun getElement(root: Element, prefix: String, localName: String): Element? {
        val ns = namespaces[prefix] ?: return null
        val nodes = root.getElementsByTagNameNS(ns, localName)
        return if (nodes.length > 0) nodes.item(0) as Element else null
    }

    companion object {
        const val META_NS = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
        const val DC_NS = "http://purl.org/dc/elements/1.1/"
        const val OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    }
}