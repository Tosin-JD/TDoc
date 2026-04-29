package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel

/**
 * Handles incremental pagination for efficient updates.
 *
 * Problem: If user edits Page 5 of a 100-page document, we don't want to
 * re-paginate all 100 pages on every keystroke. That would cause lag.
 *
 * Solution: "Dirty Region" strategy.
 * 1. Identify which element changed
 * 2. Re-measure that element
 * 3. Check if height changed:
 *    - If height unchanged: Stop! No ripple effect.
 *    - If height changed: Re-paginate from that element onwards
 * 4. Find the "Settlement Point": the page where the ripple stops
 *
 * Performance: Only re-paginate the affected pages + adjacent pages.
 */
class IncrementalPaginator(
    private val printPaginator: PrintPaginator,
    private val textMeasurer: TextMeasurer,
    private val unitConverter: UnitConverter,
    private val registry: LayoutRegistry,
    private val flowController: FlowController
) : Paginator {

    /**
     * Perform an incremental update on existing pagination.
     *
     * @param previousPages The existing pagination from before the edit
     * @param previousData The previous document data
     * @param newData The updated document data
     * @param dimensions Page dimensions
     * @param changedElementIndex Index of the changed element in newData
     * @return Updated page list (minimal re-calculation)
     */
    fun incrementalUpdate(
        previousPages: List<PageModel>,
        previousData: DocumentData,
        newData: DocumentData,
        dimensions: PageDimensions,
        changedElementIndex: Int
    ): List<PageModel> {
        if (changedElementIndex < 0 || changedElementIndex >= newData.content.size) {
            // Index out of range; do a full re-pagination
            return printPaginator.paginate(newData, dimensions)
        }

        val changedElement = newData.content[changedElementIndex]
        val previousElement = previousData.content.getOrNull(changedElementIndex)

        // Step 1: Check if height actually changed
        val previousHeight = previousElement?.let { registry.get(it.id, it) }
        val newHeight = registry.get(changedElement.id, changedElement) ?: run {
            val measured = measureElement(changedElement, dimensions)
            registry.put(changedElement.id, changedElement, measured)
            measured
        }

        if (previousHeight != null && previousHeight == newHeight) {
            // No height change; no ripple effect. Return unchanged pages.
            return previousPages
        }

        // Step 2: Find the page where the changed element is located
        val affectedPageIndex = findPageContainingElement(previousPages, changedElement.id)
            ?: return printPaginator.paginate(newData, dimensions)

        // Step 3: Re-paginate from affected page onwards
        // But first, gather pages before the affected page (they're unchanged)
        val unchangedPages = previousPages.take(affectedPageIndex)

        // Re-paginate from the changed element onwards
        val repaginatedPages = printPaginator.paginate(newData, dimensions, changedElementIndex)

        // Step 4: Find the settlement point (where the ripple ends)
        val settledPageIndex = findSettlementPoint(
            previousPages = previousPages.drop(affectedPageIndex),
            newPages = repaginatedPages,
            startPageIndex = affectedPageIndex,
            dimensions = dimensions
        )

        // Step 5: Combine:
        // - Unchanged pages before affected region
        // - New pages in affected + ripple region
        // - Unchanged pages after settlement point (renumbered)
        val result = mutableListOf<PageModel>()
        result.addAll(unchangedPages)
        result.addAll(repaginatedPages.take(settledPageIndex - affectedPageIndex + 1))

        // Renumber remaining pages if needed
        if (settledPageIndex + 1 < previousPages.size) {
            val pageCountDifference = (result.size - 1) - settledPageIndex
            for (i in settledPageIndex + 1 until previousPages.size) {
                val page = previousPages[i]
                result.add(
                    page.copy(index = page.index + pageCountDifference)
                )
            }
        }

        return result
    }

    /**
     * Detect if content was inserted or deleted (not just edited).
     *
     * @return true if content structure changed significantly
     */
    fun detectStructuralChange(
        previousData: DocumentData,
        newData: DocumentData,
        changedElementIndex: Int
    ): Boolean {
        val sizeDifference = kotlin.math.abs(previousData.content.size - newData.content.size)
        return sizeDifference > 0  // Size changed means elements were added/deleted
    }

    /**
     * Find which page in the list contains a specific element by ID.
     */
    private fun findPageContainingElement(pages: List<PageModel>, elementId: String): Int? {
        for ((pageIndex, page) in pages.withIndex()) {
            if (page.elements.any { it.elementId == elementId }) {
                return pageIndex
            }
        }
        return null
    }

    /**
     * Find the "Settlement Point": the page index where the ripple effect stabilizes.
     *
     * This happens when:
     * - The page structure matches between old and new (same elements, same page breaks)
     * - Or we reach the end of the document
     */
    private fun findSettlementPoint(
        previousPages: List<PageModel>,
        newPages: List<PageModel>,
        startPageIndex: Int,
        dimensions: PageDimensions
    ): Int {
        var pageIndex = 0
        while (pageIndex < minOf(previousPages.size, newPages.size)) {
            val oldPage = previousPages.getOrNull(pageIndex) ?: break
            val newPage = newPages.getOrNull(pageIndex) ?: break

            // Check if pages are "equivalent" (same content, same structure)
            if (!pagesAreEquivalent(oldPage, newPage)) {
                pageIndex++
            } else {
                // Found settlement point
                return startPageIndex + pageIndex
            }
        }

        // Settlement point is at the last overlapping page
        return startPageIndex + maxOf(0, minOf(previousPages.size, newPages.size) - 1)
    }

    /**
     * Check if two pages have equivalent content and structure.
     * Used to detect where the ripple effect stops.
     */
    private fun pagesAreEquivalent(page1: PageModel, page2: PageModel): Boolean {
        if (page1.elements.size != page2.elements.size) return false

        for (i in page1.elements.indices) {
            val elem1 = page1.elements[i]
            val elem2 = page2.elements[i]

            if (elem1.elementId != elem2.elementId) return false
            if (elem1.bounds != elem2.bounds) return false
        }

        return true
    }

    /**
     * Measure the height of a single element.
     */
    private fun measureElement(element: DocumentElement, dimensions: PageDimensions): Float {
        return when (element) {
            is DocumentElement.Paragraph -> {
                textMeasurer.measureParagraph(element.spans, element.style, dimensions.printableWidth)
            }
            is DocumentElement.SectionHeader -> {
                textMeasurer.measurePlainText(
                    element.text,
                    fontSize = (12 + (4 - element.level)).toFloat(),
                    isBold = true,
                    widthPt = dimensions.printableWidth
                )
            }
            is DocumentElement.Table -> {
                (element.rows.size * 20f).coerceAtLeast(40f)
            }
            is DocumentElement.Image -> {
                200f
            }
            is DocumentElement.PageBreak -> {
                0f
            }
            else -> {
                10f
            }
        }
    }

    override fun paginate(
        data: DocumentData,
        dimensions: PageDimensions,
        startIndex: Int
    ): List<PageModel> {
        // For full pagination, delegate to PrintPaginator
        return printPaginator.paginate(data, dimensions, startIndex)
    }

    override fun estimatePageCount(
        data: DocumentData,
        dimensions: PageDimensions
    ): Int {
        return printPaginator.estimatePageCount(data, dimensions)
    }

    override fun clearCache() {
        printPaginator.clearCache()
    }

    /**
     * Get statistics about the incremental update for debugging.
     */
    data class UpdateStats(
        val elementIndexChanged: Int,
        val pageAffected: Int,
        val pagesRecalculated: Int,
        val settlementPageIndex: Int,
        val heightDifference: Float
    )
}
