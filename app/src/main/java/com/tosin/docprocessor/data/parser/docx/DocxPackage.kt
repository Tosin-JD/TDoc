package com.tosin.docprocessor.data.parser.docx

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxPackage private constructor(
    private val entries: Map<String, ByteArray>
) {

    companion object {
        fun from(entries: Map<String, ByteArray>): DocxPackage = DocxPackage(LinkedHashMap(entries))

        fun from(bytes: ByteArray): DocxPackage {
            val parts = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        parts[entry.name] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
            return DocxPackage(parts)
        }
    }

    fun xml(path: String): Document? {
        val bytes = entries[path] ?: return null
        return runCatching {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isIgnoringComments = true
                isCoalescing = true
            }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        }.getOrNull()
    }

    fun text(path: String): String? = entries[path]?.decodeToString()

    fun bytes(path: String): ByteArray? = entries[path]

    fun paths(prefix: String, suffix: String = ".xml"): List<String> =
        entries.keys
            .filter { it.startsWith(prefix) && it.endsWith(suffix) }
            .sorted()

    fun relationshipsFor(partPath: String): Map<String, String> {
        val relsPath = relationshipsPathFor(partPath)
        val relsDoc = xml(relsPath) ?: return emptyMap()
        return relsDoc.documentElement
            ?.children("Relationship")
            ?.mapNotNull { rel ->
                rel.attribute("Id")?.takeIf { it.isNotBlank() }?.let { id ->
                    id to resolveTarget(partPath, rel.attribute("Target").orEmpty())
                }
            }
            ?.toMap()
            .orEmpty()
    }

    private fun relationshipsPathFor(partPath: String): String {
        val segments = partPath.split("/")
        val fileName = segments.last()
        val directory = segments.dropLast(1).joinToString("/")
        return buildString {
            if (directory.isNotBlank()) {
                append(directory)
                append("/")
            }
            append("_rels/")
            append(fileName)
            append(".rels")
        }
    }

    private fun resolveTarget(partPath: String, target: String): String {
        if (target.startsWith("/")) {
            return target.removePrefix("/")
        }
        val baseDirectory = partPath.substringBeforeLast('/', missingDelimiterValue = "")
        val baseSegments = baseDirectory.split('/').filter { it.isNotBlank() }.toMutableList()
        target.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (baseSegments.isNotEmpty()) {
                    baseSegments.removeAt(baseSegments.lastIndex)
                }
                else -> baseSegments += segment
            }
        }
        return baseSegments.joinToString("/")
    }
}

internal fun Element.children(localName: String? = null): List<Element> {
    val output = mutableListOf<Element>()
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val child = nodes.item(index)
        if (child is Element && (localName == null || child.matches(localName))) {
            output += child
        }
    }
    return output
}

internal fun Element.firstChild(localName: String): Element? = children(localName).firstOrNull()

internal fun Element.descendants(localName: String? = null): List<Element> {
    val output = mutableListOf<Element>()
    collectDescendants(this, localName, output)
    return output
}

private fun collectDescendants(node: Node, localName: String?, output: MutableList<Element>) {
    val nodes = node.childNodes
    for (index in 0 until nodes.length) {
        val child = nodes.item(index)
        if (child is Element) {
            if (localName == null || child.matches(localName)) {
                output += child
            }
            collectDescendants(child, localName, output)
        }
    }
}

internal fun Element.attribute(name: String): String? {
    if (hasAttribute(name)) {
        return getAttribute(name)
    }
    val namespaced = attributes ?: return null
    for (index in 0 until namespaced.length) {
        val item = namespaced.item(index)
        val local = item.localName ?: item.nodeName.substringAfter(':', item.nodeName)
        if (local == name || item.nodeName == name || item.nodeName.endsWith(":$name")) {
            return item.nodeValue
        }
    }
    return null
}

internal fun Element.textValue(): String = textContent.orEmpty().trim()

internal fun Element.matches(localName: String): Boolean {
    val effectiveName = this.localName ?: nodeName.substringAfter(':', nodeName)
    return effectiveName == localName
}
