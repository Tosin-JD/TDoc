package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.CompatibilityLevel
import com.tosin.docprocessor.domain.document.ParseDiagnostic

/**
 * Collects [ParseDiagnostic]s during a parse. Every mapper receives one and
 * records anything it recognises but cannot fully map, so no construct is
 * silently dropped.
 */
class DiagnosticsCollector(private val part: String? = null) {

    private val items = mutableListOf<ParseDiagnostic>()

    val diagnostics: List<ParseDiagnostic> get() = items.toList()

    fun info(construct: String, message: String, elementPath: String? = null) {
        items += ParseDiagnostic(
            severity = ParseDiagnostic.Severity.INFO,
            message = message,
            packagePart = part,
            construct = construct,
            elementPath = elementPath
        )
    }

    fun warning(construct: String, message: String, elementPath: String? = null) {
        items += ParseDiagnostic(
            severity = ParseDiagnostic.Severity.WARNING,
            message = message,
            packagePart = part,
            construct = construct,
            elementPath = elementPath,
            recoverable = true
        )
    }

    fun error(construct: String, message: String, elementPath: String? = null) {
        items += ParseDiagnostic(
            severity = ParseDiagnostic.Severity.ERROR,
            message = message,
            packagePart = part,
            construct = construct,
            elementPath = elementPath,
            recoverable = true
        )
    }

    fun preserve(construct: String, elementPath: String? = null) {
        warning(construct, "Preserved as opaque content; not rendered.", elementPath)
    }

    fun merge(other: DiagnosticsCollector) {
        items += other.diagnostics
    }

    fun merge(otherDiagnostics: List<ParseDiagnostic>) {
        items += otherDiagnostics
    }

    /** Aggregate compatibility level implied by the collected diagnostics. */
    fun compatibilityLevel(): CompatibilityLevel = when {
        items.any { it.severity == ParseDiagnostic.Severity.ERROR } ->
            CompatibilityLevel.UNSUPPORTED_WITH_WARNING
        items.any { it.severity == ParseDiagnostic.Severity.WARNING } ->
            CompatibilityLevel.PRESERVED_NOT_RENDERED
        else -> CompatibilityLevel.RENDERED_SUPPORTED
    }
}
