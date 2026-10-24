package com.tosin.docprocessor.domain.document

import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.style.TextStyle

/**
 * Evaluates deterministic document fields against a render context. Pure and
 * JVM-testable; the parser captures fields, the render pipeline evaluates them
 * per page. Fields without a resolvable value fall back to the cached result
 * captured at parse time.
 */
object FieldEvaluator {

    /** Render-time values a field may reference. */
    data class FieldContext(
        val pageNumber: Int? = null,
        val pageCount: Int? = null,
        val sectionName: String? = null,
        val date: String? = null
    )

    /**
     * @return the inlines to display for [field]: a computed text node for
     *         deterministic kinds, otherwise the cached parse-time result.
     */
    fun evaluate(field: InlineNode.Field, context: FieldContext): List<InlineNode> {
        val computed = when (field.kind?.uppercase()) {
            "PAGE" -> context.pageNumber?.toString()
            "NUMPAGES" -> context.pageCount?.toString()
            "SECTIONPAGES" -> context.pageCount?.toString()
            "SECTION" -> context.sectionName
            "DATE", "TIME" -> context.date
            else -> null
        }
        if (computed == null) return field.result
        val cachedStyle = field.result.firstOrNull() as? InlineNode.Text
        return listOf(InlineNode.Text(computed, cachedStyle?.style ?: TextStyle(), field.anchor))
    }
}
