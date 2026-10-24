package com.tosin.docprocessor.domain.document

import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.BreakKind
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
        val sectionNumber: Int? = null,
        val sectionName: String? = null,
        val date: String? = null,
        val styleReferences: Map<String, String> = emptyMap()
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
            "SECTION" -> context.sectionNumber?.toString() ?: context.sectionName
            "STYLEREF" -> {
                val requestedStyle = field.instruction.trim()
                    .removePrefix(field.kind.orEmpty())
                    .trim().trim('"')
                context.styleReferences[requestedStyle]
            }
            "DATE", "TIME" -> context.date
            else -> null
        }
        if (computed == null) return field.result
        val cachedStyle = field.result.firstOrNull() as? InlineNode.Text
        return listOf(InlineNode.Text(computed, cachedStyle?.style ?: TextStyle(), field.anchor))
    }

    /** Plain cached result lines, useful for exposing DOCX TOC entries to consumers. */
    fun cachedEntries(field: InlineNode.Field): List<String> =
        cachedText(field.result).split('\n').map(String::trim).filter(String::isNotEmpty)

    private fun cachedText(nodes: List<InlineNode>): String = buildString {
        nodes.forEach { node ->
            append(
                when (node) {
                    is InlineNode.Text -> node.text
                    is InlineNode.Span -> cachedText(node.children)
                    is InlineNode.Hyperlink -> cachedText(node.children)
                    is InlineNode.Revision -> cachedText(node.children)
                    is InlineNode.ContentControl -> cachedText(node.children)
                    is InlineNode.Field -> cachedText(node.result)
                    is InlineNode.Ruby -> cachedText(node.base)
                    is InlineNode.Tab -> "\t"
                    is InlineNode.Break -> if (node.kind == BreakKind.LINE || node.kind == BreakKind.CARRIAGE) "\n" else ""
                    else -> ""
                }
            )
        }
    }
}
