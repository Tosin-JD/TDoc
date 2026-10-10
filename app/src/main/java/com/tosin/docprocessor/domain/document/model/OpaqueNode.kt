package com.tosin.docprocessor.domain.document.model

/**
 * A recognised-but-not-semantically-mapped construct. The serialized fragment
 * keeps namespace declarations, attributes and child order intact so that
 * nothing is silently destroyed.
 */
data class OpaqueNode(
    val namespaceUri: String?,
    val localName: String,
    val serializedXml: String,
    val anchor: SourceAnchor? = null,
    val reason: String? = null
)

/** An unrecognised package part preserved verbatim for round-trip safety. */
data class OpaquePackagePart(
    val path: String,
    val mimeType: String?,
    val bytes: ByteArray,
    val role: String = "unknown"
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is OpaquePackagePart && path == other.path && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
}
