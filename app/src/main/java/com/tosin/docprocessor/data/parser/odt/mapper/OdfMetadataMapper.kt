package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.DocumentMetadata
import org.w3c.dom.Element

/** Reads `meta.xml` into a [DocumentMetadata]. Pure; no I/O. */
class OdfMetadataMapper {

    fun parse(metaRoot: Element?): DocumentMetadata {
        if (metaRoot == null) return DocumentMetadata()
        val meta = metaRoot.firstNs(Ns.OFFICE, "meta") ?: metaRoot
        val statistics = meta.firstNs(Ns.META, "document-statistic")
        val userDefined = linkedMapOf<String, String>()
        meta.elementsNs(Ns.META, "user-defined").forEach { item ->
            val name = item.attrNs(Ns.META, "name") ?: return@forEach
            userDefined[name] = item.textContent?.trim().orEmpty()
        }
        val keywords = meta.elementsNs(Ns.META, "keyword").mapNotNull { it.textContent?.trim()?.takeIf(String::isNotEmpty) }
        return DocumentMetadata(
            title = text(meta, Ns.DC, "title"),
            subject = text(meta, Ns.DC, "subject"),
            creator = text(meta, Ns.DC, "creator"),
            lastModifiedBy = text(meta, Ns.META, "printed-by"),
            description = text(meta, Ns.DC, "description"),
            keywords = keywords,
            created = text(meta, Ns.META, "creation-date"),
            modified = text(meta, Ns.DC, "date"),
            revision = text(meta, Ns.META, "editing-cycles"),
            application = text(meta, Ns.META, "generator"),
            language = text(meta, Ns.DC, "language"),
            words = statistics?.attrNs(Ns.META, "word-count")?.toIntOrNull(),
            pages = statistics?.attrNs(Ns.META, "page-count")?.toIntOrNull(),
            characters = statistics?.attrNs(Ns.META, "character-count")?.toIntOrNull(),
            paragraphs = statistics?.attrNs(Ns.META, "paragraph-count")?.toIntOrNull(),
            userDefined = userDefined
        )
    }

    private fun text(root: Element, ns: String, local: String): String? =
        root.firstNs(ns, local)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
