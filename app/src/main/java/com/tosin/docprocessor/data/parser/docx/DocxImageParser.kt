package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFPicture
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocxImageParser(private val cacheDir: File) {

    /**
     * Extracts the picture data, saves it to a temporary file,
     * and returns a DocumentElement.Image with the file path.
     */
    fun parse(poiPicture: XWPFPicture): DocumentElement.Image? {
        return try {
            val pictureData = poiPicture.pictureData ?: return null
            val extension = pictureData.suggestFileExtension() ?: "png"

            // Create a unique file in the cache directory
            val fileName = "img_${UUID.randomUUID()}.$extension"
            val imageFile = File(cacheDir, fileName)

            // Write the bytes to the file
            FileOutputStream(imageFile).use { output ->
                output.write(pictureData.data)
            }

            DocumentElement.Image(
                sourceUri = imageFile.absolutePath,
                altText = poiPicture.description ?: "Document Image",
                caption = null // POI handles captions as separate paragraphs
            )
        } catch (e: Exception) {
            com.tosin.docprocessor.data.parser.util.TDocLogger.error("Failed to parse image", e)
            null
        }
    }
}