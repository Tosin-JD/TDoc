package com.tosin.docprocessor.data.common.model

import com.tosin.docprocessor.data.parser.internal.models.TextSpan

sealed class DocumentElement {
    data class Paragraph(
        val spans: List<TextSpan>,
        val listLabel: String? = null
    ) : DocumentElement()

    data class Table(
        val rows: List<List<String>>,
        val hasHeader: Boolean = false
    ) : DocumentElement()

    data class Image(
        val sourceUri: String, // Use URI/Path instead of Bitmap to save memory
        val altText: String?,
        val caption: String? = null
    ) : DocumentElement()

    // Grouping metadata elements
    data class SectionHeader(val text: String, val level: Int = 1) : DocumentElement()

    object PageBreak : DocumentElement()
}
