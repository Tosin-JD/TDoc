package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.util.TDocLogger
import com.tosin.docprocessor.data.parser.util.sha256
import org.w3c.dom.Element
import java.io.File

class OdtImageParser(
    private val cacheDir: File,
    private val zipEntries: Map<String, ByteArray>
) {

    private val drawNs = OdtNamespaces.DRAW
    private val xlinkNs = OdtNamespaces.XLINK
    private val svgNs = OdtNamespaces.SVG

    fun parseImage(element: Element): DocumentElement.Image? {
        val imageNodes = element.getElementsByTagNameNS(drawNs, "image")
        if (imageNodes.length == 0) return null
        
        val imageElement = imageNodes.item(0) as Element
        val href = imageElement.getAttributeNS(xlinkNs, "href")
        val altText = element.getElementsByTagNameNS(svgNs, "desc").item(0)?.textContent?.trim()
        val fileName = href.substringAfterLast("/").ifBlank { "image.bin" }
        if (href.isBlank()) {
            TDocLogger.warn("ODT image element is missing href")
            return placeholderImage(sourceUri = "missing:unknown", altText = altText, reason = "Image reference missing")
        }

        val bytes = zipEntries[href]
        if (bytes == null) {
            TDocLogger.error(
                "ODT image bytes were not found in archive",
                extra = mapOf("href" to href, "availableEntries" to zipEntries.size)
            )
            return placeholderImage(sourceUri = "missing:$href", altText = altText, reason = "Missing embedded image: $fileName")
        }

        val cacheFile = saveImageToCache(bytes, fileName)
        if (cacheFile == null) {
            return placeholderImage(sourceUri = "missing:$href", altText = altText, reason = "Unable to cache image: $fileName")
        }

        return DocumentElement.Image(
            sourceUri = cacheFile.absolutePath,
            altText = altText,
            caption = null
        )
    }

    private fun saveImageToCache(bytes: ByteArray, fileName: String): File? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "bin")
        val cacheFile = File(cacheDir, "odt_img_${bytes.sha256()}.$extension")
        return try {
            if (!cacheFile.exists()) {
                cacheDir.mkdirs()
                cacheFile.outputStream().buffered().use { it.write(bytes) }
                TDocLogger.debug("Cached ODT image at ${cacheFile.absolutePath} (${bytes.size} bytes)")
            } else {
                TDocLogger.debug("Reused cached ODT image at ${cacheFile.absolutePath}")
            }
            cacheFile
        } catch (e: Exception) {
            TDocLogger.error(
                "Failed to save ODT image to cache",
                e,
                mapOf("path" to cacheFile.absolutePath, "size" to bytes.size, "fileName" to fileName)
            )
            null
        }
    }

    private fun placeholderImage(sourceUri: String, altText: String?, reason: String): DocumentElement.Image =
        DocumentElement.Image(
            sourceUri = sourceUri,
            altText = altText,
            caption = reason
        )
}
