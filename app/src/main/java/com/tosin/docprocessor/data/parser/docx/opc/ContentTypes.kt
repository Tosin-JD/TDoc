package com.tosin.docprocessor.data.parser.docx.opc

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.data.parser.util.SecureXml
import org.w3c.dom.Element

/**
 * Parsed `[Content_Types].xml`. Content types are declared via Default
 * (by extension) and Override (by part name) entries.
 */
data class ContentTypes(
    val defaults: Map<String, String> = emptyMap(),
    val overrides: Map<String, String> = emptyMap()
) {
    fun contentTypeFor(partPath: String): String? {
        val normalized = "/" + partPath.removePrefix("/")
        overrides[normalized]?.let { return it }
        val extension = partPath.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty()) defaults[extension]?.let { return it }
        return null
    }

    fun isMainDocument(partPath: String): Boolean {
        val type = contentTypeFor(partPath) ?: return false
        return type.contains("wordprocessingml.document.main") ||
            (type.contains("wordprocessingml") && type.endsWith("main+xml"))
    }

    companion object {
        const val MAIN_DOCUMENT =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

        fun parse(bytes: ByteArray?): ContentTypes {
            if (bytes == null) return ContentTypes()
            val document = runCatching { SecureXml.parse(bytes) }.getOrNull() ?: return ContentTypes()
            val root = document.documentElement ?: return ContentTypes()
            val defaults = linkedMapOf<String, String>()
            val overrides = linkedMapOf<String, String>()
            val children = root.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                when {
                    child.matchesNs(Ns.CTS, "Default") -> {
                        val extension = child.getAttribute("Extension")
                        val type = child.getAttribute("ContentType")
                        if (extension.isNotBlank() && type.isNotBlank()) {
                            defaults[extension.lowercase()] = type
                        }
                    }
                    child.matchesNs(Ns.CTS, "Override") -> {
                        val partName = child.getAttribute("PartName")
                        val type = child.getAttribute("ContentType")
                        if (partName.isNotBlank() && type.isNotBlank()) {
                            overrides["/" + partName.removePrefix("/")] = type
                        }
                    }
                }
            }
            return ContentTypes(defaults, overrides)
        }
    }
}
