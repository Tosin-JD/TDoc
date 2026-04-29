package com.tosin.docprocessor.domain.pagination

import android.content.Context
import com.tosin.docprocessor.data.common.model.DocumentElement

/**
 * Factory for creating pagination engine instances.
 * Handles dependency injection and configuration.
 */
class PaginationEngineFactory(private val context: Context) {

    /**
     * Create a complete pagination engine with all components.
     *
     * @param useIncrementalUpdates If true, uses IncrementalPaginator for better performance on edits
     * @return A configured Paginator instance
     */
    fun createPaginator(useIncrementalUpdates: Boolean = true): Paginator {
        val unitConverter = UnitConverter(context)
        val textMeasurer = TextMeasurer(context, unitConverter)
        val registry = LayoutRegistry()
        val flowController = FlowController(textMeasurer, unitConverter)

        return if (useIncrementalUpdates) {
            val printPaginator = PrintPaginator(textMeasurer, unitConverter, registry, flowController)
            IncrementalPaginator(printPaginator, textMeasurer, unitConverter, registry, flowController)
        } else {
            PrintPaginator(textMeasurer, unitConverter, registry, flowController)
        }
    }

    /**
     * Create just the TextMeasurer (for standalone text measurement).
     */
    fun createTextMeasurer(): TextMeasurer {
        val unitConverter = UnitConverter(context)
        return TextMeasurer(context, unitConverter)
    }

    /**
     * Create just the UnitConverter (for coordinate conversion).
     */
    fun createUnitConverter(): UnitConverter {
        return UnitConverter(context)
    }
}

/**
 * Extension to classify elements easily.
 */
fun DocumentElement.isFlowable(): Boolean {
    return when (this) {
        is DocumentElement.Paragraph -> true
        is DocumentElement.SectionHeader -> true
        is DocumentElement.Section -> true
        else -> false
    }
}

/**
 * Extension to classify elements as atomic.
 */
fun DocumentElement.isAtomic(): Boolean {
    return !this.isFlowable()
}

/**
 * Extension to get a human-readable type name.
 */
fun DocumentElement.getTypeName(): String {
    return when (this) {
        is DocumentElement.Paragraph -> "Paragraph"
        is DocumentElement.Table -> "Table"
        is DocumentElement.Image -> "Image"
        is DocumentElement.SectionHeader -> "Section Header"
        is DocumentElement.Section -> "Section"
        is DocumentElement.HeaderFooter -> "Header/Footer"
        is DocumentElement.Note -> "Note"
        is DocumentElement.Comment -> "Comment"
        is DocumentElement.Bookmark -> "Bookmark"
        is DocumentElement.Field -> "Field"
        is DocumentElement.Metadata -> "Metadata"
        is DocumentElement.Drawing -> "Drawing"
        is DocumentElement.EmbeddedObject -> "Embedded Object"
        is DocumentElement.PageBreak -> "Page Break"
    }
}

/**
 * Helper for debugging pagination state.
 */
data class PaginationDebugInfo(
    val totalElements: Int,
    val totalPages: Int,
    val averageElementsPerPage: Float,
    val flowableElements: Int,
    val atomicElements: Int,
    val pageBreaks: Int,
    val estimatedTotalHeight: Float
) {
    override fun toString(): String {
        return """
            Pagination Debug Info:
            - Total Elements: $totalElements
            - Total Pages: $totalPages
            - Avg Elements/Page: $averageElementsPerPage
            - Flowable Elements: $flowableElements
            - Atomic Elements: $atomicElements
            - Page Breaks: $pageBreaks
            - Est. Total Height: ${estimatedTotalHeight}pt
        """.trimIndent()
    }
}
