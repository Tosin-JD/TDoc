package com.tosin.docprocessor.domain.model.layout

/**
 * A "Ready-to-Paint" snapshot of a single sheet of paper.
 */
data class PageModel(
    val index: Int,
    val dimensions: PageDimensions,
    val elements: List<PositionedElement>,
    val headerElements: List<PositionedElement> = emptyList(),
    val footerElements: List<PositionedElement> = emptyList(),
    val sectionIndex: Int = 0,
    val state: PageState = PageState.CLEAN
)

enum class PageState { 
    CLEAN,     // Ready to render
    DIRTY,     // Needs re-calculation
    MEASURING  // Currently being processed by the engine
}
