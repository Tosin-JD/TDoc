package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.common.model.DocumentElement

/**
 * Cache that stores the measured height of every DocumentElement.
 *
 * Key insight: If the text of an element hasn't changed, its height hasn't changed either.
 * This registry avoids re-measuring the same content repeatedly.
 *
 * Design:
 * - Key: ElementId + Content Hash (ensures cache invalidation on edits)
 * - Value: Measured height in Points
 *
 * This works for incremental updates:
 * If user edits element_id_10, we compute its new hash.
 * If hash != old hash, we know the height might have changed.
 * If hash == old hash, we skip re-measuring and reuse the cached height.
 */
class LayoutRegistry {
    /**
     * Map: elementId -> (contentHash, heightInPt)
     */
    private val heightCache = mutableMapOf<String, CachedMeasurement>()

    /**
     * Map: elementId -> elementType (Paragraph, Table, Image, etc.)
     * Used to categorize elements as Atomic or Flowable.
     */
    private val elementTypeCache = mutableMapOf<String, ElementType>()

    data class CachedMeasurement(
        val contentHash: Long,
        val heightPt: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class ElementType {
        FLOWABLE,  // Can be split across pages (Paragraph, Section)
        ATOMIC,    // Must stay on one page (Image, Table, PageBreak)
        HEADER_FOOTER
    }

    /**
     * Store a measured element's height.
     *
     * @param elementId The unique ID of the element
     * @param element The element itself (for computing content hash)
     * @param heightPt The measured height in Points
     */
    fun put(elementId: String, element: DocumentElement, heightPt: Float) {
        val contentHash = computeContentHash(element)
        heightCache[elementId] = CachedMeasurement(contentHash, heightPt)
        elementTypeCache[elementId] = classifyElement(element)
    }

    /**
     * Retrieve a cached height if available and the content hasn't changed.
     *
     * @return The cached height in Points, or null if not cached or hash mismatch
     */
    fun get(elementId: String, element: DocumentElement): Float? {
        val cached = heightCache[elementId] ?: return null
        val currentHash = computeContentHash(element)
        
        // Hash mismatch means content changed; invalidate cache
        if (cached.contentHash != currentHash) {
            heightCache.remove(elementId)
            return null
        }
        
        return cached.heightPt
    }

    /**
     * Check if a given element is flowable (can be split).
     */
    fun isFlowable(elementId: String, element: DocumentElement): Boolean {
        val type = elementTypeCache[elementId] ?: classifyElement(element).also {
            elementTypeCache[elementId] = it
        }
        return type == ElementType.FLOWABLE
    }

    /**
     * Check if a given element is atomic (cannot be split).
     */
    fun isAtomic(elementId: String, element: DocumentElement): Boolean {
        return !isFlowable(elementId, element)
    }

    /**
     * Invalidate a specific element's cache.
     * Call this when the element is edited.
     */
    fun invalidate(elementId: String) {
        heightCache.remove(elementId)
        elementTypeCache.remove(elementId)
    }

    /**
     * Invalidate all cache entries from a given index onwards.
     * Call this when content is inserted/deleted (causes cascading changes).
     *
     * @param fromIndexInclusive The index from which to invalidate
     */
    fun invalidateFrom(fromIndexInclusive: Int) {
        // This is called when elements are inserted/deleted
        // Since we use element IDs (not indices), this is less critical here
        // But we provide it for incremental paginator logic
    }

    /**
     * Clear all cached data.
     * Use after loading a new document.
     */
    fun clear() {
        heightCache.clear()
        elementTypeCache.clear()
    }

    /**
     * Get cache statistics for debugging.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            totalEntries = heightCache.size,
            cacheSize = heightCache.values.sumOf { it.heightPt.toInt() }.toLong()
        )
    }

    data class CacheStats(
        val totalEntries: Int,
        val cacheSize: Long  // Sum of all heights
    )

    /**
     * Compute a hash of the element's content.
     * Used to detect changes without re-storing the entire element.
     */
    private fun computeContentHash(element: DocumentElement): Long {
        return when (element) {
            is DocumentElement.Paragraph -> {
                // Hash based on text content and styling
                val textContent = element.spans.joinToString("") { it.text }
                val styleHash = element.style.hashCode()
                (textContent.hashCode().toLong() * 31 + styleHash).toLong()
            }
            is DocumentElement.Table -> {
                // Hash based on row/column count and content
                val content = element.rows.flatten().joinToString("|")
                val metadata = element.metadata.hashCode()
                (content.hashCode().toLong() * 31 + metadata).toLong()
            }
            is DocumentElement.Image -> {
                // Hash based on source URI
                element.sourceUri.hashCode().toLong()
            }
            is DocumentElement.SectionHeader -> {
                element.text.hashCode().toLong() * 31 + element.level
            }
            is DocumentElement.PageBreak -> {
                // PageBreaks are always the same
                0xDEADBEEFL
            }
            else -> {
                // For other types, use element ID and type
                "${element::class.simpleName}_${element.id}".hashCode().toLong()
            }
        }
    }

    /**
     * Classify an element as Flowable or Atomic.
     */
    private fun classifyElement(element: DocumentElement): ElementType {
        return when (element) {
            is DocumentElement.Paragraph -> ElementType.FLOWABLE
            is DocumentElement.Table -> ElementType.FLOWABLE
            is DocumentElement.Image -> ElementType.ATOMIC
            is DocumentElement.SectionHeader -> ElementType.FLOWABLE
            is DocumentElement.Section -> ElementType.FLOWABLE
            is DocumentElement.HeaderFooter -> ElementType.HEADER_FOOTER
            is DocumentElement.PageBreak -> ElementType.ATOMIC
            else -> ElementType.ATOMIC  // Default to atomic for safety
        }
    }
}
