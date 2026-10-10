package com.tosin.docprocessor.domain.document

/**
 * Aggregate counts of [ParseDiagnostic] severities for lightweight UI display.
 * Pure; derived from a diagnostic list so the UiState stays immutable.
 */
data class DiagnosticsSummary(
    val infoCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0
) {
    val total: Int get() = infoCount + warningCount + errorCount

    val hasIssues: Boolean get() = warningCount > 0 || errorCount > 0

    companion object {
        fun from(diagnostics: List<ParseDiagnostic>): DiagnosticsSummary = DiagnosticsSummary(
            infoCount = diagnostics.count { it.severity == ParseDiagnostic.Severity.INFO },
            warningCount = diagnostics.count { it.severity == ParseDiagnostic.Severity.WARNING },
            errorCount = diagnostics.count { it.severity == ParseDiagnostic.Severity.ERROR }
        )

        fun from(summaries: Iterable<DiagnosticsSummary>): DiagnosticsSummary = DiagnosticsSummary(
            infoCount = summaries.sumOf { it.infoCount },
            warningCount = summaries.sumOf { it.warningCount },
            errorCount = summaries.sumOf { it.errorCount }
        )
    }
}
