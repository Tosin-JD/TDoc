package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
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

        val bytes = zipEntries[href] ?: return null
        
        // Save to cache
        val fileName = href.substringAfterLast("/")
        val cacheFile = File(cacheDir, "odt_img_${System.currentTimeMillis()}_$fileName")
        try {
            FileOutputStream(cacheFile).use { it.write(bytes) }
        } catch (e: Exception) {
            com.tosin.docprocessor.data.parser.util.TDocLogger.error("Failed to save ODT image to cache", e)
            return null
        }

        val altText = element.getElementsByTagNameNS(svgNs, "desc").item(0)?.textContent?.trim()

        return DocumentElement.Image(
            sourceUri = cacheFile.absolutePath,
            altText = altText,
            caption = null
        )
    }
}
