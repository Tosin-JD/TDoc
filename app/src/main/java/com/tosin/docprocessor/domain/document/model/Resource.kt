package com.tosin.docprocessor.domain.document.model

/** A binary or logical asset referenced by the document (image, object, font). */
data class Resource(
    val id: String,
    val logicalUri: String,
    val mimeType: String?,
    val contentHash: String,
    val bytes: ByteArray? = null,
    val isExternal: Boolean = false
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Resource && id == other.id && contentHash == other.contentHash)

    override fun hashCode(): Int = 31 * id.hashCode() + contentHash.hashCode()
}
