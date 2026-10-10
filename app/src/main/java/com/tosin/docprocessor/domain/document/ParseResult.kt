package com.tosin.docprocessor.domain.document

import com.tosin.docprocessor.domain.document.model.OpaquePackagePart
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.model.DocumentElement

/**
 * The import boundary between format parsers and the application.
 *
 * [document] is the rich, format-neutral tree. [content] is the temporary
 * legacy projection retained while the renderer migrates; new code should read
 * [document] and inspect [compatibility]/[diagnostics] rather than assuming an
 * import is lossless.
 */
data class ParseResult(
    val document: ParsedDocument = ParsedDocument(),
    val content: List<DocumentElement> = emptyList(),
    val diagnostics: List<ParseDiagnostic> = emptyList(),
    val compatibility: CompatibilityLevel = CompatibilityLevel.RENDERED_SUPPORTED,
    val packageInventory: PackageInventory = PackageInventory(),
    val preservedParts: List<OpaquePackagePart> = emptyList()
)

enum class CompatibilityLevel {
    RENDERED_SUPPORTED,
    PRESERVED_NOT_RENDERED,
    UNSUPPORTED_WITH_WARNING,
    REJECTED_UNSAFE
}

data class ParseDiagnostic(
    val severity: Severity,
    val message: String,
    val packagePart: String? = null,
    val construct: String? = null,
    val recoverable: Boolean = true,
    val elementPath: String? = null
) {
    enum class Severity { INFO, WARNING, ERROR }
}

/** Inventory is intentionally metadata-only: package bytes remain owned by data layer. */
data class PackageInventory(
    val entries: List<PackageEntry> = emptyList()
)

data class PackageEntry(
    val path: String,
    val sizeBytes: Long,
    val isRecognized: Boolean
)
