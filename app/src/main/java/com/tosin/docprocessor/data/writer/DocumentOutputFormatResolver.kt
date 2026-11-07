package com.tosin.docprocessor.data.writer

import com.tosin.docprocessor.domain.model.MimeTypes

object DocumentOutputFormatResolver {
    fun resolve(fileName: String, providerMimeType: String?): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMimeType = providerMimeType?.substringBefore(';')?.trim()?.lowercase()
        when (extension) {
            "docx" -> return MimeTypes.DOCX
            "odt" -> return MimeTypes.ODT
        }
        when (normalizedMimeType) {
            MimeTypes.DOCX, MimeTypes.ODT -> return normalizedMimeType
        }
        if (normalizedMimeType?.startsWith("text/") == true) return normalizedMimeType
        if (extension == "txt") return "text/plain"
        throw IllegalArgumentException(
            "Cannot save unsupported document format: ${fileName.ifBlank { providerMimeType.orEmpty() }}"
        )
    }
}
