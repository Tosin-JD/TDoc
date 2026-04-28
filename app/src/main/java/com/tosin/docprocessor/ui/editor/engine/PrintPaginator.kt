package com.tosin.docprocessor.ui.editor.engine

import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.data.common.model.layout.PositionedElement

class PrintPaginator(
    private val textMeasurer: TextMeasurer
) : Paginator {

    override fun paginate(
        documentData: DocumentData,
        pageDimensions: PageDimensions
    ): List<PageModel> {
        val pages = mutableListOf<PageModel>()
        var currentPageElements = mutableListOf<PositionedElement>()
        var currentY = pageDimensions.marginTop
        var pageNumber = 1

        val contentWidthPx = textMeasurer.pointsToPx(pageDimensions.contentWidth)

        for (element in documentData.content) {
            when (element) {
                is DocumentElement.Paragraph -> {
                    val heightPx = textMeasurer.measureParagraph(element.spans, element.style, contentWidthPx)
                    val heightPt = heightPx / 1.33f

                    if (currentY + heightPt > pageDimensions.height - pageDimensions.marginBottom) {
                        // Push to new page
                        pages.add(PageModel(pageNumber++, pageDimensions, currentPageElements))
                        currentPageElements = mutableListOf()
                        currentY = pageDimensions.marginTop
                    }

                    currentPageElements.add(
                        PositionedElement(
                            element = element,
                            x = pageDimensions.marginLeft,
                            y = currentY,
                            width = pageDimensions.contentWidth,
                            height = heightPt
                        )
                    )
                    currentY += heightPt
                }
                is DocumentElement.PageBreak -> {
                    pages.add(PageModel(pageNumber++, pageDimensions, currentPageElements))
                    currentPageElements = mutableListOf()
                    currentY = pageDimensions.marginTop
                }
                // Handle other elements (Table, Image, etc.) similarly
                else -> {
                    // For now, treat other elements as 20pt high placeholders
                    val heightPt = 20f
                    if (currentY + heightPt > pageDimensions.height - pageDimensions.marginBottom) {
                        pages.add(PageModel(pageNumber++, pageDimensions, currentPageElements))
                        currentPageElements = mutableListOf()
                        currentY = pageDimensions.marginTop
                    }
                    currentPageElements.add(
                        PositionedElement(
                            element = element,
                            x = pageDimensions.marginLeft,
                            y = currentY,
                            width = pageDimensions.contentWidth,
                            height = heightPt
                        )
                    )
                    currentY += heightPt
                }
            }
        }

        // Add the last page
        if (currentPageElements.isNotEmpty() || pages.isEmpty()) {
            pages.add(PageModel(pageNumber, pageDimensions, currentPageElements))
        }

        return pages
    }
}
