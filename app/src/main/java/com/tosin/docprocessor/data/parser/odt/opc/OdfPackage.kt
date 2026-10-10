package com.tosin.docprocessor.data.parser.odt.opc

import com.tosin.docprocessor.data.parser.util.SecureXml
import org.w3c.dom.Document

/**
 * Reader for an OpenDocument package (ZIP). Access is read-only; callers own the
 * entry bytes. Main content is discovered via the mandatory `content.xml`.
 */
class OdfPackage private constructor(
    val entries: Map<String, ByteArray>,
    val manifest: OdfManifest
) {

    fun mimetype(): String? = entries["mimetype"]?.toString(Charsets.UTF_8)?.trim()

    fun contentBytes(): ByteArray? = entries["content.xml"]

    fun part(path: String): ByteArray? = entries[path]

    fun document(path: String): Document? = entries[path]?.let { runCatching { SecureXml.parse(it) }.getOrNull() }

    fun partsWithExtension(extension: String): List<String> =
        entries.keys.filter { it.endsWith(".$extension", ignoreCase = true) }.sorted()

    companion object {
        fun parse(entries: Map<String, ByteArray>): OdfPackage =
            OdfPackage(entries, OdfManifest.parse(entries["META-INF/manifest.xml"]))
    }
}
