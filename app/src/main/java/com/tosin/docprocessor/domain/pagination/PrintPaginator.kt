package com.tosin.docprocessor.domain.pagination

import android.graphics.RectF
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.data.common.model.layout.PageState
import com.tosin.docprocessor.data.common.model.layout.PositionedElement
import com.tosin.docprocessor.data.common.model.layout.TableCellLayout
import com.tosin.docprocessor.data.common.model.layout.TableRenderLayout
import com.tosin.docprocessor.data.common.model.layout.TableRowLayout
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import kotlin.math.ceil

class PrintPaginator(
    private val textMeasurer: TextMeasurer,
    private val unitConverter: UnitConverter,
    private val registry: LayoutRegistry,
    private val flowController: FlowController
) : Paginator {
    private val tableCellPaddingPt = 6f

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

        if (state.currentPageElements.isNotEmpty()) {
            state.pages.add(createPage(state.currentPageIndex, state.currentPageElements, dimensions))
        }

        return state.pages
    }

    private fun paginateElement(
        element: DocumentElement,
        elementIndex: Int,
        dimensions: PageDimensions,
        state: PaginationState
    ) {
        if (flowController.isPageBreak(element)) {
            if (state.currentPageElements.isNotEmpty()) {
                advanceToNextPage(state, dimensions)
            } else {
                state.currentPageIndex++
                state.currentY = dimensions.marginTop
            }
            return
        }

        var pending: DocumentElement? = element

        while (pending != null) {
            val currentElement = pending
            val elementHeight = measureElement(currentElement, dimensions)
            val availableSpace = dimensions.printableHeight - (state.currentY - dimensions.marginTop)

            if (elementHeight <= availableSpace) {
                addElementToPage(
                    currentElement,
                    state.currentY,
                    state.currentPageIndex,
                    dimensions,
                    state.currentPageElements
                )
                state.currentY += elementHeight
                pending = null
            } else if (flowController.shouldNotSplit(currentElement)) {
                if (state.currentPageElements.isNotEmpty()) {
                    advanceToNextPage(state, dimensions)
                }
                addElementToPage(
                    currentElement,
                    state.currentY,
                    state.currentPageIndex,
                    dimensions,
                    state.currentPageElements
                )
                state.currentY += elementHeight
                pending = null
            } else {
                val (fragment1, fragment2) = splitElement(
                    element = currentElement,
                    availableHeightPt = availableSpace,
                    widthPt = dimensions.printableWidth
                )

                val firstHeight = fragment1?.let { measureElement(it, dimensions) } ?: 0f
                val madeProgress = fragment1 != null && firstHeight > 0f

                if (fragment1 != null) {
                    addElementToPage(
                        fragment1,
                        state.currentY,
                        state.currentPageIndex,
                        dimensions,
                        state.currentPageElements
                    )
                    state.currentY += firstHeight
                }

                pending = fragment2

                if (pending != null) {
                    if (state.currentPageElements.isNotEmpty()) {
                        advanceToNextPage(state, dimensions)
                    } else if (!madeProgress) {
                        addElementToPage(
                            pending,
                            state.currentY,
                            state.currentPageIndex,
                            dimensions,
                            state.currentPageElements
                        )
                        state.currentY += measureElement(pending, dimensions)
                        pending = null
                    }
                }
            }
        }
    }

    private fun splitElement(
        element: DocumentElement,
        availableHeightPt: Float,
        widthPt: Float
    ): Pair<DocumentElement?, DocumentElement?> {
        return when (element) {
            is DocumentElement.Paragraph -> splitParagraphElement(element, availableHeightPt, widthPt)
            is DocumentElement.Table -> splitTableElement(element, availableHeightPt, widthPt)
            else -> Pair(element, null)
        }
    }

    private fun splitParagraphElement(
        element: DocumentElement.Paragraph,
        availableHeightPt: Float,
        widthPt: Float
    ): Pair<DocumentElement?, DocumentElement?> {
        if (element.spans.isEmpty()) return Pair(element, null)

        val lastFittingLine = textMeasurer.getLastFittingLine(
            element.spans,
            element.style,
            widthPt,
            availableHeightPt
        )

        if (lastFittingLine < 0) {
            return Pair(null, element)
        }

        val fullLayout = textMeasurer.buildParagraphLayout(
            spans = element.spans,
            style = element.style,
            widthPt = widthPt
        ) ?: return Pair(element, null)

        val adjustedLine = flowController.adjustSplitPointForRules(fullLayout.lineCount, lastFittingLine)
        if (adjustedLine < 0) {
            return Pair(null, element)
        }

        val charAtLineEnd = textMeasurer.getLineEnd(fullLayout, adjustedLine)
        val (spans1, spans2) = splitSpans(element.spans, charAtLineEnd)
        val fragment1 = element.copy(spans = spans1).takeIf { it.spans.isNotEmpty() }
        val fragment2 = element.copy(spans = spans2).takeIf { it.spans.isNotEmpty() }
        return Pair(fragment1, fragment2)
    }

    private fun splitTableElement(
        element: DocumentElement.Table,
        availableHeightPt: Float,
        widthPt: Float
    ): Pair<DocumentElement?, DocumentElement?> {
        if (element.rows.isEmpty()) return Pair(element, null)

        val layout = buildTableLayout(element, widthPt)
        if (layout.rowLayouts.isEmpty()) return Pair(element, null)

        val headerRows = if (element.hasHeader) 1 else 0
        var consumedHeight = 0f
        var rowsThatFit = 0

        layout.rowLayouts.forEachIndexed { index, rowLayout ->
            val nextHeight = consumedHeight + rowLayout.heightPt
            if (nextHeight <= availableHeightPt) {
                consumedHeight = nextHeight
                rowsThatFit = index + 1
            }
        }

        if (rowsThatFit <= headerRows) {
            return Pair(null, element)
        }

        if (rowsThatFit >= element.rows.size) {
            return Pair(element, null)
        }

        val firstRows = element.rows.take(rowsThatFit)
        val remainingRows = buildList {
            if (element.hasHeader) {
                add(element.rows.first())
            }
            addAll(element.rows.drop(rowsThatFit))
        }

        return Pair(
            element.copy(rows = firstRows),
            element.copy(rows = remainingRows)
        )
    }

    private fun splitSpans(
        spans: List<TextSpan>,
        splitCharPosition: Int
    ): Pair<List<TextSpan>, List<TextSpan>> {
        val spans1 = mutableListOf<TextSpan>()
        val spans2 = mutableListOf<TextSpan>()

        var charCount = 0
        for (span in spans) {
            val spanEnd = charCount + span.text.length

            when {
                spanEnd <= splitCharPosition -> spans1.add(span)
                charCount >= splitCharPosition -> spans2.add(span)
                else -> {
                    val splitInSpan = splitCharPosition - charCount
                    spans1.add(span.copy(text = span.text.substring(0, splitInSpan)))
                    spans2.add(span.copy(text = span.text.substring(splitInSpan)))
                }
            }

            charCount = spanEnd
        }

        return Pair(spans1, spans2)
    }

    private fun measureElement(element: DocumentElement, dimensions: PageDimensions): Float {
        registry.get(element.id, element)?.let { return it }

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
                buildTableLayout(element, dimensions.printableWidth).totalHeightPt.coerceAtLeast(24f)
            }
            is DocumentElement.Image -> 200f
            is DocumentElement.PageBreak -> 0f
            else -> 10f
        }

        registry.put(element.id, element, height)
        return height
    }

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

        pageElements.add(
            PositionedElement(
                elementId = element.id,
                element = element,
                pageIndex = pageIndex,
                bounds = bounds,
                metadata = mapOf("originalHeight" to elementHeight),
                layoutResult = createLayoutResult(element, dimensions.printableWidth)
            )
        )
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
            is DocumentElement.Table -> buildTableLayout(element, widthPt)
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

    private fun buildTableLayout(
        element: DocumentElement.Table,
        widthPt: Float
    ): TableRenderLayout {
        val columnCount = element.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val cellWidthPt = widthPt / columnCount
        val contentWidthPt = (cellWidthPt - (tableCellPaddingPt * 2f)).coerceAtLeast(12f)

        val rowLayouts = element.rows.ifEmpty { listOf(emptyList()) }.mapIndexed { rowIndex, row ->
            val cells = List(columnCount) { columnIndex ->
                val text = row.getOrNull(columnIndex).orEmpty()
                val layout = textMeasurer.buildPlainTextLayout(
                    text = text.ifEmpty { " " },
                    fontSize = 10f,
                    isBold = element.hasHeader && rowIndex == 0,
                    widthPt = contentWidthPt
                )

                TableCellLayout(
                    text = text,
                    widthPt = cellWidthPt,
                    contentHeightPt = layout?.let { unitConverter.pxToPt(it.height.toFloat()) } ?: 0f,
                    layout = layout
                )
            }

            val rowHeight = (cells.maxOfOrNull { it.contentHeightPt } ?: 0f) + (tableCellPaddingPt * 2f)
            TableRowLayout(
                heightPt = rowHeight.coerceAtLeast(24f),
                cells = cells,
                isHeader = element.hasHeader && rowIndex == 0
            )
        }

        return TableRenderLayout(
            columnCount = columnCount,
            rowLayouts = rowLayouts,
            cellPaddingPt = tableCellPaddingPt
        )
    }

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

    private fun createEmptyPage(index: Int, dimensions: PageDimensions): PageModel {
        return PageModel(
            index = index,
            dimensions = dimensions,
            elements = emptyList(),
            state = PageState.CLEAN
        )
    }

    private fun advanceToNextPage(
        state: PaginationState,
        dimensions: PageDimensions
    ) {
        state.pages.add(createPage(state.currentPageIndex, state.currentPageElements, dimensions))
        state.currentPageElements = mutableListOf()
        state.currentPageIndex++
        state.currentY = dimensions.marginTop
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
                is DocumentElement.Table -> buildTableLayout(element, dimensions.printableWidth).totalHeightPt
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
