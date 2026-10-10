package com.tosin.docprocessor.data.parser.docx.opc

import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.docx.DocxPackage
import org.w3c.dom.Document

/**
 * Reader for an Open Packaging Conventions (OPC) container: content types,
 * relationship graph and part access. Main-document discovery uses the root
 * relationships rather than a hard-coded path.
 */
class OpcPackage private constructor(
    val entries: Map<String, ByteArray>,
    val contentTypes: ContentTypes,
    val rootRelationships: Map<String, DocxPackage.Relationship>
) {

    private val docxPackage = DocxPackage.from(entries)

    fun part(path: String): ByteArray? = entries[path]

    fun document(path: String): Document? = runCatching { docxPackage.xml(path)!! }.getOrNull()

    fun relationshipsFor(part: String): Map<String, DocxPackage.Relationship> =
        docxPackage.relationshipDetailsFor(part)

    fun mainDocumentPart(): String? {
        val candidate = rootRelationships.values.firstOrNull {
            it.type?.substringAfterLast('/')?.equals("officeDocument", ignoreCase = true) == true
        }?.target
        if (candidate != null && entries.containsKey(candidate)) return candidate
        return entries.keys.firstOrNull { contentTypes.isMainDocument(it) }
            ?: entries.keys.firstOrNull { it.equals("word/document.xml", ignoreCase = true) }
    }

    fun partByRelationshipType(part: String, typeSuffix: String): String? =
        relationshipsFor(part).values.firstOrNull {
            it.type?.substringAfterLast('/')?.equals(typeSuffix, ignoreCase = true) == true && !it.isExternal
        }?.target

    fun partsByRelationshipType(part: String, typeSuffix: String): List<String> =
        relationshipsFor(part).values.filter {
            it.type?.substringAfterLast('/')?.equals(typeSuffix, ignoreCase = true) == true && !it.isExternal
        }.map { it.target }

    companion object {
        fun parse(entries: Map<String, ByteArray>, diagnostics: DiagnosticsCollector): OpcPackage {
            val contentTypes = ContentTypes.parse(entries["[Content_Types].xml"])
            if (entries["[Content_Types].xml"] == null) {
                diagnostics.warning("package", "Missing [Content_Types].xml", "[Content_Types].xml")
            }
            val docxPackage = DocxPackage.from(entries)
            val rootRelationships = if (entries.containsKey("_rels/.rels")) {
                docxPackage.relationshipDetailsFor("")
            } else {
                diagnostics.warning("package", "Missing root relationships (_rels/.rels)", "_rels/.rels")
                emptyMap()
            }
            return OpcPackage(entries, contentTypes, rootRelationships)
        }
    }
}
