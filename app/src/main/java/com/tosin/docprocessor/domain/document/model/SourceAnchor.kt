package com.tosin.docprocessor.domain.document.model

/**
 * Stable, format-neutral pointer back into the source package. Used to join
 * reference nodes with their definitions and to attach diagnostics.
 */
data class SourceAnchor(
    val part: String,
    val elementPath: String? = null,
    val relationshipId: String? = null,
    val nativeId: String? = null
)
