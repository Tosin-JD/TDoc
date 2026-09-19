package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.DrawingInfo
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
    private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"

    fun parseImage(element: Element): DocumentElement.Image? {
        // handleImage can be either a containing element (draw:frame) or the
        // draw:image element itself (directly in the text flow).
        val imageElement = when {
            element.localName == "image" && element.namespaceURI == drawNs -> element
            else -> {
                val imageNodes = element.getElementsByTagNameNS(drawNs, "image")
                if (imageNodes.length == 0) return null
                imageNodes.item(0) as Element
            }
        }
        val href = imageElement.getAttributeNS(xlinkNs, "href")
        if (href.isEmpty()) return null

        val bytes = zipEntries[href] ?: return null

        // Save to cache
        val fileName = href.substringAfterLast("/").ifEmpty { "image.bin" }
        val cacheFile = File(cacheDir, "odt_img_${System.currentTimeMillis()}_$fileName")
        try {
            FileOutputStream(cacheFile).use { it.write(bytes) }
        } catch (e: Exception) {
            return null
        }

        val altText = element.getElementsByTagNameNS(svgNs, "desc").item(0)?.textContent?.trim()
        val title = imageElement.getAttributeNS(officeNs, "title").ifEmpty { null }

        return DocumentElement.Image(
            sourceUri = cacheFile.toURI().toString(),
            altText = altText ?: title,
            caption = imageElement.getAttributeNS(officeNs, "name").ifEmpty { null }
        )
    }

    fun parseDrawing(element: Element): DocumentElement.Drawing? {
        val imageNodes = element.getElementsByTagNameNS(drawNs, "image")
        val widthEmu = element.getAttributeNS(svgNs, "width")
            .removeSuffix("cm").ifEmpty { null }?.let {
                (it.toDoubleOrNull() ?: 0.0) * 360000
            }?.toLong()
        val heightEmu = element.getAttributeNS(svgNs, "height")
            .removeSuffix("cm").ifEmpty { null }?.let {
                (it.toDoubleOrNull() ?: 0.0) * 360000
            }?.toLong()
        val altText = element.getElementsByTagNameNS(svgNs, "desc").item(0)?.textContent?.trim()

        return DocumentElement.Drawing(
            DrawingInfo(
                kind = if (imageNodes.length > 0) "image" else "frame",
                isInline = false,
                widthEmu = widthEmu,
                heightEmu = heightEmu,
                altText = altText,
                title = element.getAttributeNS(officeNs, "title").ifEmpty { null }
            )
        )
    }
}