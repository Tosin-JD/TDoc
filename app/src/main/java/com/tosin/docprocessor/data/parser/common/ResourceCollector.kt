package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.model.Resource
import java.security.MessageDigest

/**
 * Accumulates package resources with stable ids and content hashes. The domain
 * stores logical package URIs only; materialising to Android cache is a
 * repository concern.
 */
class ResourceCollector {

    private val collected = linkedMapOf<String, Resource>()

    fun add(
        logicalUri: String,
        mimeType: String? = null,
        bytes: ByteArray? = null,
        isExternal: Boolean = false
    ): String {
        val id = logicalUri
        if (collected.containsKey(id)) return id
        collected[id] = Resource(
            id = id,
            logicalUri = logicalUri,
            mimeType = mimeType ?: guessMimeType(logicalUri),
            contentHash = bytes?.let(::hash) ?: logicalUri,
            bytes = bytes,
            isExternal = isExternal
        )
        return id
    }

    fun resources(): Map<String, Resource> = collected.toMap()

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun guessMimeType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        "tif", "tiff" -> "image/tiff"
        "emf" -> "image/emf"
        "wmf" -> "image/wmf"
        else -> null
    }
}
