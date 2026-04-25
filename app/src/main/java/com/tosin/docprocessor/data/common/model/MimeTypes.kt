package com.tosin.docprocessor.data.common.model

object MimeTypes {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val ODT = "application/vnd.oasis.opendocument.text"

    fun fromExtension(extension: String): String? = when (extension.lowercase()) {
        "docx" -> DOCX
        "odt" -> ODT
        else -> null
    }
}
