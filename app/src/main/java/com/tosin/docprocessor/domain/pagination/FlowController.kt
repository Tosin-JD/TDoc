package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PositionedElement

/**
 * Manages pagination rules and flow control.
 *
 * Rules implemented:
 * - **Widows & Orphans:** Prevent single lines of text from appearing alone at page boundaries
 * - **Keep With Next:** Keep a heading with the paragraph that follows
 * - **Keep Together:** Don't split a paragraph or table across pages
 * - **Page Breaks:** Respect explicit page break elements
 *
 * These rules are typographic best practices that make documents more readable.
 */
class FlowController(
    private val textMeasurer: TextMeasurer,
    private val unitConverter: UnitConverter
) {
    /**
     * Pagination rules configuration.
     */
    data class PaginationRules(
        val minLinesBefore: Int = 2,      // Minimum lines at bottom of page (Widows)
        val minLinesAfter: Int = 2,       // Minimum lines at top of page (Orphans)
        val keepWithNextElements: Set<String> = setOf("SectionHeader"),
        val noSplitElements: Set<String> = setOf("Table", "Image"),
        val respectPageBreaks: Boolean = true
    )

    private var rules = PaginationRules()

    /**
     * Update the pagination rules.
     */
    fun setRules(newRules: PaginationRules) {
        rules = newRules
    }

    /**
     * Check if an element should be kept with the next element.
     * Typically applies to headings.
     */
    fun shouldKeepWithNext(element: DocumentElement): Boolean {
        return when (element) {
            is DocumentElement.SectionHeader -> true
            else -> false
        }
    }

    /**
     * Check if an element should never be split across pages.
     */
    fun shouldNotSplit(element: DocumentElement): Boolean {
        return when (element) {
            is DocumentElement.Table -> true
            is DocumentElement.Image -> true
            is DocumentElement.PageBreak -> true
            else -> false
        }
    }

    /**
     * Check if an element is a forced page break.
     */
    fun isPageBreak(element: DocumentElement): Boolean {
        return element is DocumentElement.PageBreak
    }

    /**
     * Check if a line count meets the minimum line requirements.
     * Used to prevent Widows and Orphans.
     *
     * @param totalLines The total number of lines in the paragraph
     * @param linesToPlace The number of lines we're trying to place at page boundary
     * @param isAtPageEnd true if these lines are at the bottom of a page (Widows check)
     * @return true if the line count is acceptable
     */
    fun meetsLineRequirements(totalLines: Int, linesToPlace: Int, isAtPageEnd: Boolean): Boolean {
        return if (isAtPageEnd) {
            // At page end: ensure we're not leaving isolated lines (Widows)
            linesToPlace >= rules.minLinesBefore || linesToPlace == totalLines
        } else {
            // At page start: ensure we're not starting with isolated lines (Orphans)
            linesToPlace >= rules.minLinesAfter || linesToPlace == totalLines
        }
    }

    /**
     * Determine the best split point for a paragraph to avoid Widows/Orphans.
     *
     * @param totalLines Total number of lines in the paragraph
     * @param lastFittingLine The line that fits if we ignore Widow/Orphan rules
     * @return The adjusted line number to use for splitting
     */
    fun adjustSplitPointForRules(totalLines: Int, lastFittingLine: Int): Int {
        val remainingLines = totalLines - (lastFittingLine + 1)
        
        // If remaining lines are too few, move more lines to next page
        if (remainingLines < rules.minLinesAfter && remainingLines > 0) {
            // Move lines back to avoid Orphans
            val adjustedPoint = (lastFittingLine - rules.minLinesBefore).coerceAtLeast(-1)
            return adjustedPoint
        }

        // If lines at page end are too few, move them to next page
        if ((lastFittingLine + 1) < rules.minLinesBefore && lastFittingLine + 1 > 0) {
            return -1  // Don't split; move entire paragraph to next page
        }

        return lastFittingLine
    }

    /**
     * Check if we should force a page break before this element.
     */
    fun shouldPageBreakBefore(element: DocumentElement, previousElement: DocumentElement?): Boolean {
        if (!rules.respectPageBreaks) return false
        
        return previousElement?.let { isPageBreak(it) } ?: false
    }

    /**
     * Check if we should force a page break after this element.
     */
    fun shouldPageBreakAfter(element: DocumentElement): Boolean {
        if (!rules.respectPageBreaks) return false
        
        return isPageBreak(element)
    }

    /**
     * Validate that a page doesn't start with a heading followed by nothing.
     * Returns true if the page structure is valid.
     */
    fun isValidPageStart(pageElements: List<PositionedElement>): Boolean {
        if (pageElements.isEmpty()) return true
        
        val firstElement = pageElements.firstOrNull()?.element ?: return true
        
        // Page shouldn't start with a heading that has no following content
        if (shouldKeepWithNext(firstElement) && pageElements.size == 1) {
            return false
        }
        
        return true
    }

    /**
     * Get a human-readable description of why pagination happened the way it did.
     * Useful for debugging layout issues.
     */
    fun describeBreakReason(element: DocumentElement, reason: String): String {
        return when {
            isPageBreak(element) -> "Explicit page break"
            shouldNotSplit(element) -> "Element is atomic (cannot split)"
            shouldKeepWithNext(element) -> "Element should stay with next"
            reason.contains("widow") -> "Widow prevention: not enough lines at page bottom"
            reason.contains("orphan") -> "Orphan prevention: not enough lines at page top"
            else -> reason
        }
    }
}
