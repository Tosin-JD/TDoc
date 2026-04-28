package com.tosin.docprocessor.data.common.model.layout

data class PageModel(
    val pageNumber: Int,
    val dimensions: PageDimensions,
    val elements: List<PositionedElement>
)
