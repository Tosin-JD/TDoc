package com.tosin.docprocessor.data.parser.odt.opc

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.util.SecureXml

/**
 * Parsed `META-INF/manifest.xml`: declared full-path → media-type, plus
 * encryption entries. Byte payloads stay in [OdfPackage].
 */
data class OdfManifest(
    val mediaTypes: Map<String, String> = emptyMap(),
    val encryptedPaths: Set<String> = emptySet()
) {
    companion object {
        fun parse(bytes: ByteArray?): OdfManifest {
            if (bytes == null) return OdfManifest()
            val document = runCatching { SecureXml.parse(bytes) }.getOrNull() ?: return OdfManifest()
            val root = document.documentElement ?: return OdfManifest()
            val mediaTypes = linkedMapOf<String, String>()
            val encrypted = linkedSetOf<String>()
            root.elementsNs(Ns.MANIFEST, "file-entry").forEach { entry ->
                val path = entry.attrNs(Ns.MANIFEST, "full-path") ?: return@forEach
                entry.attrNs(Ns.MANIFEST, "media-type")?.let { mediaTypes[path] = it }
                if (entry.attrNs(Ns.MANIFEST, "encryption-data") != null) encrypted += path
            }
            return OdfManifest(mediaTypes, encrypted)
        }
    }
}
