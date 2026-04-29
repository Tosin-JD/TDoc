package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel

/**
 * Core interface for the pagination engine.
 *
 * The paginator is the "Brain" of the word processor.
 * Its job: Take a stream of content and divide it into physical pages.
 */
interface Paginator {
    /**
     * Paginate a document into pages.
     *
     * @param data The document content to paginate
     * @param dimensions The physical page dimensions and margins
     * @param startIndex Optional: Start re-paginating from this element index
     *                   Used for incremental updates. Default = 0 (full paginate)
     * @return A list of PageModels, one per page
     */
    fun paginate(
        data: DocumentData,
        dimensions: PageDimensions,
        startIndex: Int = 0
    ): List<PageModel>

    /**
     * Estimate the total number of pages without fully paginating.
     * Useful for progress indicators or quick estimates.
     *
     * @return Estimated page count (may be off by ±1-2 pages for complex documents)
     */
    fun estimatePageCount(
        data: DocumentData,
        dimensions: PageDimensions
    ): Int

    /**
     * Clear any internal caches.
     * Call after major document changes.
     */
    fun clearCache()
}
