package com.tosin.docprocessor.data.common.model

object MimeTypes {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val ODT = "application/vnd.oasis.opendocument.text"
    const val TXT = "text/plain"

    fun fromExtension(extension: String): String? = when (extension.lowercase()) {
        "docx" -> DOCX
        "odt" -> ODT
        "txt" -> TXT
        else -> null
    }

    fun fromFileName(fileName: String): String? =
        fromExtension(fileName.substringAfterLast('.', ""))

    fun isSupportedMime(mimeType: String): Boolean =
        mimeType == DOCX || mimeType == ODT || mimeType == TXT

    fun supportedMimes(): Array<String> = arrayOf(DOCX, ODT, TXT)
}
