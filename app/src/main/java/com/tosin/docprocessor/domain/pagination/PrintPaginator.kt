package com.tosin.docprocessor.domain.pagination

import android.graphics.RectF
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.ElementIndentation
import com.tosin.docprocessor.domain.model.layout.LayoutRect
import com.tosin.docprocessor.domain.model.layout.LayoutResult
import com.tosin.docprocessor.domain.model.layout.PageDimensions
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.model.layout.PageState
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.domain.model.layout.TableCellLayout
import com.tosin.docprocessor.domain.model.layout.TableRenderLayout
import com.tosin.docprocessor.domain.model.layout.TableRowLayout
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import kotlin.math.ceil

class PrintPaginator(
    private val textMeasurer: TextMeasurer,
    private val unitConverter: UnitConverter,
    private val registry: LayoutRegistry,
    private val flowController: FlowController,
    private val imageDimensionsProvider: ImageDimensionsProvider,
    private val imageLayoutConfig: ImageLayoutConfig = ImageLayoutConfig.Default
) : Paginator {
    private val tableCellPaddingPt = 4f
    private val minTableColumnWidthPt = 48f
    private val maxTableColumnWeight = 8f

    private val sectionResolver = SectionResolver()
    private val indentationResolver = IndentationResolver()
    private val spacingResolver = SpacingResolver()

    private val measuredImageLayouts = mutableMapOf<String, LayoutResult.ImageLayout>()

    private data class PaginationState(
        var currentPageIndex: Int = 0,
        var currentY: Float = 0f,
        var currentDimensions: PageDimensions = PageDimensions(),
        var currentPageElements: MutableList<PositionedElement> = mutableListOf(),
        val pages: MutableList<PageModel> = mutableListOf(),
        val sectionStartPages: MutableSet<Int> = mutableSetOf(0)
    )

    override fun paginate(
        data: DocumentData,
        dimensions: PageDimensions,
        startIndex: Int
    ): List<PageModel> = paginate(data, dimensions, startIndex, isDarkMode = false)

    fun paginate(
        data: DocumentData,
        dimensions: PageDimensions,
        startIndex: Int = 0,
        isDarkMode: Boolean = false
    ): List<PageModel> {
        val headerFooterElements = data.content.filterIsInstance<DocumentElement.HeaderFooter>()
        val headers = headerFooterElements.filter { it.content.kind == HeaderFooterKind.HEADER }
        val footers = headerFooterElements.filter { it.content.kind == HeaderFooterKind.FOOTER }

        val baseDimensions = applyHeaderFooterReservation(dimensions, headers, footers)

        val elementsToProcess = data.content
            .drop(startIndex)
            .filterNot {
                it is DocumentElement.HeaderFooter ||
                    it is DocumentElement.Note ||
                    it is DocumentElement.Comment ||
                    it is DocumentElement.Bookmark ||
                    it is DocumentElement.Field ||
                    it is DocumentElement.Metadata
            }

        measuredImageLayouts.clear()

        if (elementsToProcess.isEmpty()) {
            val page = createEmptyPage(0, baseDimensions)
            return listOf(attachHeaderFooter(page, headers, footers, setOf(0), isDarkMode))
        }

        val state = PaginationState(
            currentY = baseDimensions.bodyTop,
            currentDimensions = baseDimensions
        )

        for ((index, element) in elementsToProcess.withIndex()) {
            if (element is DocumentElement.Section) {
                val newDimensions = applyHeaderFooterReservation(
                    sectionResolver.resolve(element.properties),
                    headers,
                    footers
                )
                if (state.currentPageElements.isNotEmpty()) {
                    state.pages.add(
                        createPage(state.currentPageIndex, state.currentPageElements, state.currentDimensions)
                    )
                    state.currentPageElements = mutableListOf()
                    state.currentPageIndex++
                }
                state.currentDimensions = newDimensions
                state.currentY = newDimensions.bodyTop
                state.sectionStartPages.add(state.currentPageIndex)
                continue
            }

            paginateElement(
                element = element,
                elementIndex = startIndex + index,
                dimensions = state.currentDimensions,
                state = state,
                isDarkMode = isDarkMode
            )
        }

        if (state.currentPageElements.isNotEmpty()) {
            state.pages.add(
                createPage(state.currentPageIndex, state.currentPageElements, state.currentDimensions)
            )
        }

        return state.pages.map { page ->
            attachHeaderFooter(page, headers, footers, state.sectionStartPages, isDarkMode)
        }
    }

    private fun applyHeaderFooterReservation(
        dimensions: PageDimensions,
        headers: List<DocumentElement.HeaderFooter>,
        footers: List<DocumentElement.HeaderFooter>
    ): PageDimensions {
        if (headers.isEmpty() && footers.isEmpty()) return dimensions

        val headerHeightPt = headers.firstOrNull()?.let {
            textMeasurer.measurePlainText(it.content.text, fontSize = 10f, widthPt = dimensions.printableWidth)
        } ?: 0f
        val footerHeightPt = footers.firstOrNull()?.let {
            textMeasurer.measurePlainText(it.content.text, fontSize = 10f, widthPt = dimensions.printableWidth)
        } ?: 0f

        return dimensions.copy(headerHeight = headerHeightPt, footerHeight = footerHeightPt)
    }

    private fun attachHeaderFooter(
        page: PageModel,
        headers: List<DocumentElement.HeaderFooter>,
        footers: List<DocumentElement.HeaderFooter>,
        sectionStartPages: Set<Int>,
        isDarkMode: Boolean
    ): PageModel {
        if (headers.isEmpty() && footers.isEmpty()) return page

        val isFirstPageOfSection = page.index in sectionStartPages
        val header = resolveHeaderForPage(headers, page.index, isFirstPageOfSection)
        val footer = resolveHeaderForPage(footers, page.index, isFirstPageOfSection)

        return page.copy(
            headerElements = header?.let {
                listOf(createHeaderFooterElement(it, page.dimensions.headerRect, page.index, isDarkMode))
            } ?: emptyList(),
            footerElements = footer?.let {
                listOf(createHeaderFooterElement(it, page.dimensions.footerRect, page.index, isDarkMode))
            } ?: emptyList()
        )
    }

    private fun resolveHeaderForPage(
        candidates: List<DocumentElement.HeaderFooter>,
        pageIndex: Int,
        isFirstPageOfSection: Boolean
    ): DocumentElement.HeaderFooter? {
        if (candidates.isEmpty()) return null
        val variants = candidates.associateBy { it.content.variant }
        return when {
            isFirstPageOfSection && variants.containsKey("first") -> variants["first"]
            pageIndex % 2 == 1 && variants.containsKey("even") -> variants["even"]
            else -> variants["default"] ?: candidates.first()
        }
    }

    private fun createHeaderFooterElement(
        element: DocumentElement.HeaderFooter,
        rect: LayoutRect,
        pageIndex: Int,
        isDarkMode: Boolean
    ): PositionedElement {
        val bounds = RectF(rect.left, rect.top, rect.right, rect.bottom)
        return PositionedElement(
            elementId = element.id,
            element = element,
            pageIndex = pageIndex,
            bounds = bounds,
            layoutResult = buildTextLayoutResult(element.content.text, rect.width(), isDarkMode)
        )
    }

    private fun paginateElement(
        element: DocumentElement,
        elementIndex: Int,
        dimensions: PageDimensions,
        state: PaginationState,
        isDarkMode: Boolean = false
    ) {
        if (flowController.isPageBreak(element)) {
            if (state.currentPageElements.isNotEmpty()) {
                advanceToNextPage(state)
            } else {
                state.currentPageIndex++
                state.currentY = dimensions.bodyTop
            }
            return
        }

        if (element is DocumentElement.Paragraph && element.style.pageBreakBefore) {
            if (state.currentPageElements.isNotEmpty()) {
                advanceToNextPage(state)
            }
        }

        var pending: DocumentElement? = element

        while (pending != null) {
            val currentElement = pending
            val elementHeight = measureElement(currentElement, dimensions)
            val availableSpace = dimensions.bodyHeight - (state.currentY - dimensions.bodyTop)

            if (elementHeight <= availableSpace) {
                addElementToPage(
                    currentElement,
                    state.currentY,
                    state.currentPageIndex,
                    dimensions,
                    state.currentPageElements,
                    isDarkMode
                )
                state.currentY += elementHeight
                pending = null
            } else if (flowController.shouldNotSplit(currentElement) ||
                (currentElement is DocumentElement.Paragraph && currentElement.style.keepWithNext) ||
                (elementHeight <= dimensions.bodyHeight &&
                    flowController.shouldMoveToOwnPage(elementHeight, dimensions.bodyHeight))
            ) {
                if (state.currentPageElements.isNotEmpty()) {
                    advanceToNextPage(state)
                }
                addElementToPage(
                    currentElement,
                    state.currentY,
                    state.currentPageIndex,
                    dimensions,
                    state.currentPageElements,
                    isDarkMode
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
                        state.currentPageElements,
                        isDarkMode
                    )
                    state.currentY += firstHeight
                }

                pending = fragment2

                if (pending != null) {
                    if (state.currentPageElements.isNotEmpty()) {
                        advanceToNextPage(state)
                    } else if (!madeProgress) {
                        addElementToPage(
                            pending,
                            state.currentY,
                            state.currentPageIndex,
                            dimensions,
                            state.currentPageElements,
                            isDarkMode
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

        val indent = indentationResolver.resolve(element.style.indentation, element.listInfo)

        val lastFittingLine = textMeasurer.getLastFittingLine(
            element.renderSpans(),
            element.style,
            widthPt,
            availableHeightPt,
            indent
        )

        if (lastFittingLine < 0) {
            return Pair(null, element)
        }

        val fullLayout = textMeasurer.buildParagraphLayout(
            spans = element.renderSpans(),
            style = element.style,
            widthPt = widthPt,
            indentation = indent
        ) ?: return Pair(element, null)

        val adjustedLine = flowController.adjustSplitPointForRules(fullLayout.lineCount, lastFittingLine)
        if (adjustedLine < 0) {
            return Pair(null, element)
        }

        val charAtLineEnd = textMeasurer.getLineEnd(fullLayout, adjustedLine)
        val (spans1, spans2) = splitSpans(element.renderSpans(), charAtLineEnd)
        val fragment1 = element.copy(spans = spans1, listLabel = null).takeIf { it.spans.isNotEmpty() }
        val fragment2 = element.copy(spans = spans2, listLabel = null).takeIf { it.spans.isNotEmpty() }
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
                val indent = indentationResolver.resolve(element.style.indentation, element.listInfo)
                val spacing = spacingResolver.resolve(element.style.spacing)
                val textHeight = textMeasurer.measureParagraph(
                    element.renderSpans(),
                    element.style,
                    dimensions.printableWidth,
                    indent
                )
                spacing.beforePt + textHeight + spacing.afterPt
            }
            is DocumentElement.SectionHeader -> {
                val headingSize = element.style.spacing.line?.let { it / 20f }
                    ?: element.style.indentation.left?.let { null } // not used
                    ?: headingFontSizeFromStyle(element)
                textMeasurer.measurePlainText(
                    element.text,
                    fontSize = headingSize,
                    isBold = true,
                    widthPt = dimensions.printableWidth
                )
            }
            is DocumentElement.Table -> {
                buildTableLayout(element, dimensions.printableWidth).totalHeightPt.coerceAtLeast(24f)
            }
            is DocumentElement.Image -> measureImage(element, dimensions)
            is DocumentElement.Drawing -> measureDrawing(element, dimensions)
            is DocumentElement.Section -> 0f
            is DocumentElement.PageBreak -> 0f
            else -> 10f
        }

        registry.put(element.id, element, height)
        return height
    }

    private fun measureImage(
        element: DocumentElement.Image,
        dimensions: PageDimensions,
        isDarkMode: Boolean = false
    ): Float {
        val layout = resolveImageLayout(
            element = element,
            availableWidthPt = dimensions.printableWidth,
            maxHeightPt = dimensions.bodyHeight * imageLayoutConfig.maxHeightFraction,
            isDarkMode = isDarkMode
        )
        measuredImageLayouts[element.id] = layout

        val captionHeight = layout.captionLayout?.let { unitConverter.pxToPt(it.height.toFloat()) } ?: 0f
        val captionSpacing = if (captionHeight > 0f) imageLayoutConfig.captionSpacingPt else 0f
        return layout.displayHeightPt + captionSpacing + captionHeight
    }

    private fun resolveImageLayout(
        element: DocumentElement.Image,
        availableWidthPt: Float,
        maxHeightPt: Float,
        isDarkMode: Boolean = false
    ): LayoutResult.ImageLayout {
        val intrinsic = imageDimensionsProvider.intrinsicPoints(element.sourceUri)
        val scaled = ImageScaleCalculator.scale(
            intrinsicWidth = intrinsic?.widthPt,
            intrinsicHeight = intrinsic?.heightPt,
            availableWidth = availableWidthPt,
            maxHeight = maxHeightPt,
            fallbackHeight = imageLayoutConfig.fallbackHeightPt
        )

        val caption = element.caption?.takeIf { it.isNotBlank() }
        val captionLayout = caption?.let {
            textMeasurer.buildPlainTextLayout(
                text = it,
                fontSize = 10f,
                widthPt = availableWidthPt,
                isDarkMode = isDarkMode
            )
        }

        return LayoutResult.ImageLayout(
            intrinsicWidthPt = scaled.intrinsicWidth,
            intrinsicHeightPt = scaled.intrinsicHeight,
            displayWidthPt = scaled.displayWidth,
            displayHeightPt = scaled.displayHeight,
            sourceUri = element.sourceUri,
            caption = caption,
            captionLayout = captionLayout
        )
    }

    private fun measureDrawing(element: DocumentElement.Drawing, dimensions: PageDimensions): Float {
        val widthPt = element.info.widthEmu?.let { it / 914400f * 72f }
        val heightPt = element.info.heightEmu?.let { it / 914400f * 72f }
        if (widthPt == null || heightPt == null || widthPt <= 0f) return 200f

        val scale = if (widthPt > dimensions.printableWidth) dimensions.printableWidth / widthPt else 1f
        return (heightPt * scale).coerceAtLeast(1f)
    }

    private fun addElementToPage(
        element: DocumentElement,
        yPositionPt: Float,
        pageIndex: Int,
        dimensions: PageDimensions,
        pageElements: MutableList<PositionedElement>,
        isDarkMode: Boolean = false
    ) {
        val elementHeight = measureElement(element, dimensions)
        val indent = when (element) {
            is DocumentElement.Paragraph ->
                indentationResolver.resolve(element.style.indentation, element.listInfo)
            else -> ElementIndentation()
        }
        val paragraphSpacingBefore = (element as? DocumentElement.Paragraph)
            ?.let { spacingResolver.resolve(it.style.spacing).beforePt }
            ?: 0f

        val bounds = RectF(
            dimensions.marginLeft + indent.leftPt,
            yPositionPt + paragraphSpacingBefore,
            dimensions.marginLeft + dimensions.printableWidth - indent.rightPt,
            yPositionPt + elementHeight
        )

        pageElements.add(
            PositionedElement(
                elementId = element.id,
                element = element,
                pageIndex = pageIndex,
                bounds = bounds,
                indentation = indent,
                metadata = mapOf("originalHeight" to elementHeight),
                layoutResult = createLayoutResult(element, dimensions, indent, isDarkMode)
            )
        )
    }

    private fun createLayoutResult(
        element: DocumentElement,
        dimensions: PageDimensions,
        indentation: ElementIndentation = ElementIndentation(),
        isDarkMode: Boolean = false
    ): LayoutResult {
        val widthPt = dimensions.printableWidth
        return when (element) {
            is DocumentElement.Paragraph ->
                textMeasurer.buildParagraphLayout(
                    element.renderSpans(),
                    element.style,
                    widthPt,
                    indentation,
                    isDarkMode
                )
                    ?.let { LayoutResult.TextLayout(it) }
                    ?: LayoutResult.Empty

            is DocumentElement.SectionHeader ->
                textMeasurer.buildPlainTextLayout(
                    text = element.text,
                    fontSize = headingFontSizeFromStyle(element),
                    isBold = true,
                    widthPt = widthPt,
                    isDarkMode = isDarkMode
                )?.let { LayoutResult.TextLayout(it) } ?: LayoutResult.Empty

            is DocumentElement.Table ->
                LayoutResult.TableLayout(buildTableLayout(element, widthPt, indentation, isDarkMode))

            is DocumentElement.Image ->
                measuredImageLayouts[element.id]
                    ?: resolveImageLayout(
                        element = element,
                        availableWidthPt = dimensions.printableWidth,
                        maxHeightPt = dimensions.bodyHeight * imageLayoutConfig.maxHeightFraction,
                        isDarkMode = isDarkMode
                    )

            is DocumentElement.Drawing ->
                buildTextLayoutResult("drawing: ${element.info.kind}", widthPt, isDarkMode)

            is DocumentElement.Note ->
                buildTextLayoutResult(
                    "${element.info.kind.name.lowercase()}: ${element.info.text}",
                    widthPt,
                    isDarkMode
                )

            is DocumentElement.Comment ->
                buildTextLayoutResult(
                    "comment by ${element.info.author ?: "unknown"}: ${element.info.text}",
                    widthPt,
                    isDarkMode
                )

            is DocumentElement.Bookmark ->
                buildTextLayoutResult(
                    "bookmark ${element.info.boundary.name.lowercase()}: ${element.info.name.ifBlank { element.info.id }}",
                    widthPt,
                    isDarkMode
                )

            is DocumentElement.Field ->
                buildTextLayoutResult(
                    "field ${element.info.type}: ${element.info.instruction}",
                    widthPt,
                    isDarkMode
                )

            is DocumentElement.Metadata ->
                buildTextLayoutResult(
                    buildString {
                        append(element.info.title ?: element.info.kind)
                        append(": ")
                        append(element.info.summary)
                    },
                    widthPt,
                    isDarkMode
                )

            is DocumentElement.EmbeddedObject ->
                buildTextLayoutResult(
                    "embedded object: ${element.info.programId ?: element.info.kind}",
                    widthPt,
                    isDarkMode
                )

            else -> LayoutResult.Empty
        }
    }

    private fun buildTextLayoutResult(
        text: String,
        widthPt: Float,
        isDarkMode: Boolean
    ): LayoutResult {
        val layout = textMeasurer.buildPlainTextLayout(
            text = text,
            fontSize = 10f,
            widthPt = widthPt,
            isDarkMode = isDarkMode
        )
        return layout?.let { LayoutResult.TextLayout(it) } ?: LayoutResult.Empty
    }

    private fun headingFontSize(level: Int): Float = when (level) {
        1 -> 16f
        2 -> 13f
        3 -> 12f
        else -> 11f
    }

    private fun headingFontSizeFromStyle(element: DocumentElement.SectionHeader): Float {
        val style = element.style
        // Try to derive from common style properties if available; fallback to heuristic
        return headingFontSize(element.level)
    }

    private fun buildTableLayout(
        element: DocumentElement.Table,
        widthPt: Float,
        indentation: ElementIndentation = ElementIndentation(),
        isDarkMode: Boolean = false
    ): TableRenderLayout {
        val effectiveWidth = (widthPt - indentation.leftPt - indentation.rightPt)
            .coerceAtLeast(minTableColumnWidthPt)

        val columnCount = element.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val columnWidthsPt = if (element.gridColumnWidthsPt.isNotEmpty() && element.gridColumnWidthsPt.size == columnCount) {
            val gridSum = element.gridColumnWidthsPt.sum()
            if (gridSum > 0f) {
                val scale = effectiveWidth / gridSum
                element.gridColumnWidthsPt.map { (it * scale).coerceAtLeast(minTableColumnWidthPt) }
            } else {
                calculateColumnWidths(element.rows, effectiveWidth, columnCount)
            }
        } else {
            calculateColumnWidths(element.rows, effectiveWidth, columnCount)
        }

        val rowLayouts = element.rows.ifEmpty { listOf(emptyList()) }.mapIndexed { rowIndex, row ->
            val cells = List(columnCount) { columnIndex ->
                val text = row.getOrNull(columnIndex).orEmpty()
                val cellWidthPt = columnWidthsPt[columnIndex]
                val contentWidthPt = (cellWidthPt - (tableCellPaddingPt * 2f)).coerceAtLeast(20f)
                val layout = textMeasurer.buildPlainTextLayout(
                    text = text.ifEmpty { " " },
                    fontSize = 9f,
                    isBold = element.hasHeader && rowIndex == 0,
                    widthPt = contentWidthPt,
                    isDarkMode = isDarkMode
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

    private fun calculateColumnWidths(
        rows: List<List<String>>,
        totalWidthPt: Float,
        columnCount: Int
    ): List<Float> {
        if (columnCount == 1) return listOf(totalWidthPt)

        val rawWeights = MutableList(columnCount) { 1f }
        rows.forEach { row ->
            repeat(columnCount) { columnIndex ->
                val text = row.getOrNull(columnIndex).orEmpty()
                val estimatedWeight = estimateColumnWeight(text)
                rawWeights[columnIndex] = maxOf(rawWeights[columnIndex], estimatedWeight)
            }
        }

        val totalWeight = rawWeights.sum().takeIf { it > 0f } ?: columnCount.toFloat()
        val initialWidths = rawWeights.map { weight ->
            maxOf(minTableColumnWidthPt, totalWidthPt * (weight / totalWeight))
        }.toMutableList()

        val widthSum = initialWidths.sum()
        if (widthSum == totalWidthPt) return initialWidths

        val scale = totalWidthPt / widthSum
        val normalized = initialWidths.map { it * scale }.toMutableList()
        val normalizedSum = normalized.sum()
        if (normalized.isNotEmpty()) {
            normalized[normalized.lastIndex] += totalWidthPt - normalizedSum
        }
        return normalized
    }

    private fun estimateColumnWeight(text: String): Float {
        val longestSegment = text
            .lineSequence()
            .map { it.trim().length }
            .maxOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        return (longestSegment / 8f).coerceIn(1f, maxTableColumnWeight)
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

    private fun advanceToNextPage(state: PaginationState) {
        state.pages.add(
            createPage(state.currentPageIndex, state.currentPageElements, state.currentDimensions)
        )
        state.currentPageElements = mutableListOf()
        state.currentPageIndex++
        state.currentY = state.currentDimensions.bodyTop
    }

    override fun estimatePageCount(
        data: DocumentData,
        dimensions: PageDimensions
    ): Int {
        if (data.content.isEmpty()) return 1

        val bodyHeight = dimensions.bodyHeight.takeIf { it > 0f } ?: dimensions.printableHeight
        var totalHeightPt = 0f
        for (element in data.content) {
            totalHeightPt += when (element) {
                is DocumentElement.Paragraph -> {
                    val indent = indentationResolver.resolve(element.style.indentation, element.listInfo)
                    val spacing = spacingResolver.resolve(element.style.spacing)
                    spacing.beforePt +
                        textMeasurer.measureParagraph(element.spans, element.style, dimensions.printableWidth, indent) +
                        spacing.afterPt
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
                is DocumentElement.Image -> measureImage(element, dimensions)
                is DocumentElement.Drawing -> measureDrawing(element, dimensions)
                else -> 10f
            }
        }

        return ceil(totalHeightPt / bodyHeight).toInt().coerceAtLeast(1)
    }

    override fun clearCache() {
        registry.clear()
        textMeasurer.clearCache()
    }

    private fun DocumentElement.Paragraph.renderSpans(): List<TextSpan> =
        listLabel?.let { listOf(TextSpan(text = "$it ")) + spans } ?: spans
}
