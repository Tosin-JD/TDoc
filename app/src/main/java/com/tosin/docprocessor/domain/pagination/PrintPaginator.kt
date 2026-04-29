package com.tosin.docprocessor.domain.pagination

import android.graphics.RectF
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.data.common.model.layout.PageState
import com.tosin.docprocessor.data.common.model.layout.PositionedElement
import kotlin.math.ceil

/**
 * The core pagination algorithm: "Pouring" content into fixed-size pages.
 *
 * Algorithm Flow:
 * 1. Initialize: Start Page 1, currentY = marginTop
 * 2. Iterate: For each DocumentElement
 * 3. Measure: Get element height using TextMeasurer
 * 4. Evaluate:
 *    - If fits: Add to current page, move currentY down
 *    - If overflows:
 *      - Splittable (Paragraph): Find split point, place fragments on separate pages
 *      - Atomic (Image/Table): Move entire element to next page
 * 5. Repeat until all elements processed
 *
 * Handles:
 * - Text wrapping and line breaks
 * - Widow/Orphan prevention (FlowController)
 * - Atomic elements (cannot split)
 * - Page breaks
 * - Header/Footer placement (basic)
 */
class PrintPaginator(
    private val textMeasurer: TextMeasurer,
    private val unitConverter: UnitConverter,
    private val registry: LayoutRegistry,
    private val flowController: FlowController
) : Paginator {

    /**
     * Internal state for tracking pagination progress.
     */
    private data class PaginationState(
        var currentPageIndex: Int = 0,
        var currentY: Float = 0f,
        var currentPageElements: MutableList<PositionedElement> = mutableListOf(),
        val pages: MutableList<PageModel> = mutableListOf()
    )

    override fun paginate(
        data: DocumentData,
        dimensions: PageDimensions,
        startIndex: Int
    ): List<PageModel> {
        if (data.content.isEmpty()) {
            return listOf(createEmptyPage(0, dimensions))
        }

        val state = PaginationState(currentY = dimensions.marginTop)
        val elementsToProcess = data.content.drop(startIndex)

        for ((index, element) in elementsToProcess.withIndex()) {
            paginateElement(
                element = element,
                elementIndex = startIndex + index,
                dimensions = dimensions,
                state = state
            )
        }

        // Finalize the last page
        if (state.currentPageElements.isNotEmpty()) {
            state.pages.add(
                PageModel(
                    index = state.currentPageIndex,
                    dimensions = dimensions,
                    elements = state.currentPageElements,
                    state = PageState.CLEAN
                )
            )
        }

        return state.pages
    }

    /**
     * Process a single element and place it on the appropriate page(s).
     */
    private fun paginateElement(
        element: DocumentElement,
        elementIndex: Int,
        dimensions: PageDimensions,
        state: PaginationState
    ) {
        // Handle page breaks
        if (flowController.isPageBreak(element)) {
            // Start a new page
            if (state.currentPageElements.isNotEmpty()) {
                state.pages.add(createPage(state.currentPageIndex, state.currentPageElements, dimensions))
                state.currentPageElements = mutableListOf()
            }
            state.currentPageIndex++
            state.currentY = dimensions.marginTop
            return
        }

        val elementHeight = measureElement(element, dimensions)

        // Check if element fits on current page
        val availableSpace = dimensions.printableHeight - (state.currentY - dimensions.marginTop)

        if (elementHeight <= availableSpace) {
            // Element fits on current page
            addElementToPage(element, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements)
            state.currentY += elementHeight
        } else if (flowController.shouldNotSplit(element)) {
            // Element is atomic: move it to next page
            if (state.currentPageElements.isNotEmpty()) {
                state.pages.add(createPage(state.currentPageIndex, state.currentPageElements, dimensions))
                state.currentPageElements = mutableListOf()
            }
            state.currentPageIndex++
            state.currentY = dimensions.marginTop
            
            // Add to new page
            addElementToPage(element, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements)
            state.currentY += elementHeight
        } else {
            // Element is splittable (Paragraph): split it
            val (fragment1, fragment2) = splitElement(
                element = element,
                availableHeightPt = availableSpace,
                widthPt = dimensions.printableWidth
            )

            if (fragment1 != null) {
                addElementToPage(fragment1, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements)
            }

            // Move to next page with fragment2
            if (fragment2 != null) {
                state.pages.add(createPage(state.currentPageIndex, state.currentPageElements, dimensions))
                state.currentPageElements = mutableListOf()
                state.currentPageIndex++
                state.currentY = dimensions.marginTop

                addElementToPage(fragment2, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements)
                val fragment2Height = measureElement(fragment2, dimensions)
                state.currentY += fragment2Height
            }
        }
    }

    /**
     * Split a flowable element (Paragraph) into two fragments.
     *
     * @return Pair of (fragment1, fragment2), or (element, null) if no split needed
     */
    private fun splitElement(
        element: DocumentElement,
        availableHeightPt: Float,
        widthPt: Float
    ): Pair<DocumentElement?, DocumentElement?> {
        return when (element) {
            is DocumentElement.Paragraph -> {
                if (element.spans.isEmpty()) {
                    return Pair(element, null)
                }

                val text = element.spans.joinToString("") { it.text }
                val lastFittingLine = textMeasurer.getLastFittingLine(
                    element.spans,
                    element.style,
                    widthPt,
                    availableHeightPt
                )

                if (lastFittingLine < 0) {
                    // Nothing fits; return the element as-is for next page
                    Pair(null, element)
                } else {
                    // Adjust for Widow/Orphan rules
                    val fullLayout = textMeasurer.buildParagraphLayout(
                        spans = element.spans,
                        style = element.style,
                        widthPt = widthPt
                    ) ?: return Pair(element, null)
                    val lineCount = fullLayout.lineCount
                    val adjustedLine = flowController.adjustSplitPointForRules(lineCount, lastFittingLine)

                    if (adjustedLine < 0) {
                        // Don't split; move entire paragraph to next page
                        Pair(null, element)
                    } else {
                        // Create text fragments at line boundary
                        val charAtLineEnd = textMeasurer.getLineEnd(
                            fullLayout,
                            adjustedLine
                        )

                        // Split the spans based on character position
                        val (spans1, spans2) = splitSpans(element.spans, charAtLineEnd)

                        val fragment1 = element.copy(spans = spans1).takeIf { it.spans.isNotEmpty() }
                        val fragment2 = element.copy(spans = spans2).takeIf { it.spans.isNotEmpty() }

                        Pair(fragment1, fragment2)
                    }
                }
            }
            else -> Pair(element, null)  // Atomic elements shouldn't reach here
        }
    }

    /**
     * Split text spans at a character position.
     * Maintains the text integrity and span properties.
     */
    private fun splitSpans(
        spans: List<com.tosin.docprocessor.data.parser.internal.models.TextSpan>,
        splitCharPosition: Int
    ): Pair<List<com.tosin.docprocessor.data.parser.internal.models.TextSpan>, List<com.tosin.docprocessor.data.parser.internal.models.TextSpan>> {
        val spans1 = mutableListOf<com.tosin.docprocessor.data.parser.internal.models.TextSpan>()
        val spans2 = mutableListOf<com.tosin.docprocessor.data.parser.internal.models.TextSpan>()

        var charCount = 0
        for (span in spans) {
            val spanEnd = charCount + span.text.length

            when {
                spanEnd <= splitCharPosition -> {
                    // Entire span goes to first half
                    spans1.add(span)
                }
                charCount >= splitCharPosition -> {
                    // Entire span goes to second half
                    spans2.add(span)
                }
                else -> {
                    // Span is split
                    val splitInSpan = splitCharPosition - charCount
                    val text1 = span.text.substring(0, splitInSpan)
                    val text2 = span.text.substring(splitInSpan)

                    spans1.add(span.copy(text = text1))
                    spans2.add(span.copy(text = text2))
                }
            }
            charCount = spanEnd
        }

        return Pair(spans1, spans2)
    }

    /**
     * Measure the height of an element.
     * Uses cache when available.
     */
    private fun measureElement(element: DocumentElement, dimensions: PageDimensions): Float {
        // Check cache first
        val cached = registry.get(element.id, element)
        if (cached != null) {
            return cached
        }

        val height = when (element) {
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
                // Simple table height estimation: rows * 20pt per row
                (element.rows.size * 20f).coerceAtLeast(40f)
            }
            is DocumentElement.Image -> {
                // Assume 200pt height for images (adjust based on actual aspect ratio)
                200f
            }
            is DocumentElement.PageBreak -> {
                0f  // Page breaks have no height
            }
            else -> {
                // Default to minimal height for unknown types
                10f
            }
        }

        // Cache the measurement
        registry.put(element.id, element, height)
        return height
    }

    /**
     * Add an element to the current page.
     */
    private fun addElementToPage(
        element: DocumentElement,
        yPositionPt: Float,
        pageIndex: Int,
        dimensions: PageDimensions,
        pageElements: MutableList<PositionedElement>
    ) {
        val elementHeight = measureElement(element, dimensions)
        val bounds = RectF(
            dimensions.marginLeft,
            yPositionPt,
            dimensions.marginLeft + dimensions.printableWidth,
            yPositionPt + elementHeight
        )

        val positioned = PositionedElement(
            elementId = element.id,
            element = element,
            pageIndex = pageIndex,
            bounds = bounds,
            metadata = mapOf("originalHeight" to elementHeight),
            layoutResult = createLayoutResult(element, dimensions.printableWidth)
        )

        pageElements.add(positioned)
    }

    private fun createLayoutResult(
        element: DocumentElement,
        widthPt: Float
    ): Any? {
        return when (element) {
            is DocumentElement.Paragraph -> {
                textMeasurer.buildParagraphLayout(element.spans, element.style, widthPt)
            }
            is DocumentElement.SectionHeader -> {
                textMeasurer.buildPlainTextLayout(
                    text = element.text,
                    fontSize = (12 + (4 - element.level)).toFloat(),
                    isBold = true,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Section -> {
                textMeasurer.buildPlainTextLayout(
                    text = element.properties.toString(),
                    fontSize = 11f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.HeaderFooter -> {
                textMeasurer.buildPlainTextLayout(
                    text = "${element.content.kind.name.lowercase()} (${element.content.variant}): ${element.content.text}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Note -> {
                textMeasurer.buildPlainTextLayout(
                    text = "${element.info.kind.name.lowercase()}: ${element.info.text}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Comment -> {
                textMeasurer.buildPlainTextLayout(
                    text = "comment by ${element.info.author ?: "unknown"}: ${element.info.text}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Bookmark -> {
                textMeasurer.buildPlainTextLayout(
                    text = "bookmark ${element.info.boundary.name.lowercase()}: ${element.info.name.ifBlank { element.info.id }}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Field -> {
                textMeasurer.buildPlainTextLayout(
                    text = "field ${element.info.type}: ${element.info.instruction}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Metadata -> {
                textMeasurer.buildPlainTextLayout(
                    text = buildString {
                        append(element.info.title ?: element.info.kind)
                        append(": ")
                        append(element.info.summary)
                    },
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.Drawing -> {
                textMeasurer.buildPlainTextLayout(
                    text = "drawing: ${element.info.kind}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            is DocumentElement.EmbeddedObject -> {
                textMeasurer.buildPlainTextLayout(
                    text = "embedded object: ${element.info.programId ?: element.info.kind}",
                    fontSize = 10f,
                    widthPt = widthPt
                )
            }
            else -> null
        }
    }

    /**
     * Create a page model from elements.
     */
    private fun createPage(
        index: Int,
        elements: List<PositionedElement>,
        dimensions: PageDimensions
    ): PageModel {
        return PageModel(
            index = index,
            dimensions = dimensions,
            elements = elements,
            state = PageState.CLEAN
        )
    }

    /**
     * Create an empty page.
     */
    private fun createEmptyPage(index: Int, dimensions: PageDimensions): PageModel {
        return PageModel(
            index = index,
            dimensions = dimensions,
            elements = emptyList(),
            state = PageState.CLEAN
        )
    }

    override fun estimatePageCount(
        data: DocumentData,
        dimensions: PageDimensions
    ): Int {
        if (data.content.isEmpty()) return 1

        var totalHeightPt = 0f
        for (element in data.content) {
            totalHeightPt += when (element) {
                is DocumentElement.Paragraph -> {
                    textMeasurer.measureParagraph(element.spans, element.style, dimensions.printableWidth)
                }
                is DocumentElement.SectionHeader -> {
                    textMeasurer.measurePlainText(
                        element.text,
                        fontSize = 14f,
                        isBold = true,
                        widthPt = dimensions.printableWidth
                    )
                }
                is DocumentElement.Table -> (element.rows.size * 20f).coerceAtLeast(40f)
                is DocumentElement.Image -> 200f
                else -> 10f
            }
        }

        return ceil(totalHeightPt / dimensions.printableHeight).toInt().coerceAtLeast(1)
    }

    override fun clearCache() {
        registry.clear()
        textMeasurer.clearCache()
    }
}
