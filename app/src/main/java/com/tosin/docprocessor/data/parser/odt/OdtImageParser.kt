package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.domain.model.DocumentElement
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream

class OdtImageParser(
    private val cacheDir: File,
    private val zipEntries: Map<String, ByteArray>
) {

    private val drawNs = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private val xlinkNs = "http://www.w3.org/1999/xlink"
    private val svgNs = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"

    fun parseImage(element: Element): DocumentElement.Image? {
        val imageNodes = element.getElementsByTagNameNS(drawNs, "image")
        if (imageNodes.length == 0) return null
        
        val imageElement = imageNodes.item(0) as Element
        val href = imageElement.getAttributeNS(xlinkNs, "href")
        if (href.isEmpty()) return null

        val bytes = zipEntries[href]
        if (bytes == null) {
            // Linked images cannot be copied from the package; retain the
            // reference so a renderer can surface a recoverable placeholder.
            return DocumentElement.Image(
                sourceUri = href,
                altText = description(element),
                caption = element.getAttributeNS(drawNs, "name").takeIf { it.isNotBlank() }
            )
        }
        
        // Save to cache
        val suffix = href.substringAfterLast('.', "bin").let { ".${it.take(16)}" }
        val cacheFile = File.createTempFile("odt_", suffix, cacheDir)
        try {
            FileOutputStream(cacheFile).use { it.write(bytes) }
        } catch (e: Exception) {
            com.tosin.docprocessor.data.parser.util.TDocLogger.error("Failed to save ODT image to cache", e)
            return null
        }

        return DocumentElement.Image(
            sourceUri = cacheFile.absolutePath,
            altText = description(element),
            caption = element.getAttributeNS(drawNs, "name").takeIf { it.isNotBlank() }
        )
    }

    private fun description(element: Element): String? =
        element.getElementsByTagNameNS(svgNs, "desc").item(0)?.textContent?.trim()
            ?.takeIf { it.isNotEmpty() }
}
