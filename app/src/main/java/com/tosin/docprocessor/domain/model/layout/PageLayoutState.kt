package com.tosin.docprocessor.domain.model.layout

data class PageLayoutState(
    val pageCount: Int = 1,
    val currentPage: Int = 1
) {
    init {
        require(pageCount >= 1)
        require(currentPage in 1..pageCount)
    }
}
