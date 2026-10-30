package com.tosin.docprocessor.domain.model.layout

import com.tosin.docprocessor.domain.model.RenderableElement

/**
 * A "Ready-to-Paint" snapshot of a single sheet of paper.
 */
data class PageModel(
    val index: Int,
    val dimensions: PageDimensions,
    val elements: List<RenderableElement>,
    val headerElements: List<RenderableElement> = emptyList(),
    val footerElements: List<RenderableElement> = emptyList(),
    val sectionIndex: Int = 0,
    val state: PageState = PageState.CLEAN,
    val stableKey: String = "",
    val documentFingerprint: Long = 0L
)

enum class PageState { 
    CLEAN,     // Ready to render
    DIRTY,     // Needs re-calculation
    MEASURING  // Currently being processed by the engine
}
