package com.tosin.docprocessor.domain.pagination

/**
 * # Pagination Engine Layer - Complete Documentation
 *
 * ## Overview
 *
 * The Pagination Engine is the "Brain" of TDoc. Its job is to take a continuous stream
 * of document content and divide it into physical pages based on:
 * - Page dimensions (width, height, margins)
 * - Text measurement (how tall is each paragraph?)
 * - Typographic rules (Widows/Orphans prevention)
 * - Flow control (keep headings with content, respect page breaks)
 *
 * ## Architecture
 *
 * The engine is organized into layers:
 *
 * ```
 * ┌─────────────────────────────────────────┐
 * │  Paginator Interface                    │  <- Public API
 * │  (paginate, estimatePageCount)          │
 * ├─────────────────────────────────────────┤
 * │  PrintPaginator (full algorithm)        │
 * │  IncrementalPaginator (fast updates)    │
 * ├─────────────────────────────────────────┤
 * │  TextMeasurer                           │  <- Domain Logic
 * │  FlowController                         │
 * │  LayoutRegistry                         │
 * ├─────────────────────────────────────────┤
 * │  UnitConverter                          │  <- Utilities
 * └─────────────────────────────────────────┘
 * ```
 *
 * ## Components
 *
 * ### 1. UnitConverter
 * Converts between Points (PT), Pixels (PX), and Inches.
 * - All external measurements use Points (industry standard for documents)
 * - All Android measurements use Pixels
 * - Prevents floating-point rounding errors
 *
 * **Usage:**
 * ```kotlin
 * val converter = UnitConverter(context)
 * val widthPx = converter.ptToPx(612f)  // 8.5 inches = 612 points
 * val heightPt = converter.pxToPt(100f) // Convert back
 * ```
 *
 * ### 2. TextMeasurer
 * Measures text height using Android's StaticLayout (fast, accurate, low-overhead).
 *
 * **Key Methods:**
 * - `measureParagraph()`: Get the height of a paragraph
 * - `measurePlainText()`: Get the height of simple text
 * - `getLastFittingLine()`: Binary search for which line fits in available space
 *
 * **Usage:**
 * ```kotlin
 * val measurer = TextMeasurer(context, unitConverter)
 * val height = measurer.measureParagraph(
 *     spans = paragraph.spans,
 *     style = paragraph.style,
 *     widthPt = 468f  // Page width minus margins
 * )
 * ```
 *
 * ### 3. LayoutRegistry
 * Caches the measured height of each element to avoid re-measuring unchanged content.
 *
 * **How It Works:**
 * - Computes a content hash of each element (text for Paragraph, rows for Table)
 * - Stores: elementId -> (contentHash, cachedHeight)
 * - On edit: If content hash changes, cache is invalidated
 * - If content hash stays same, reuse cached height (no re-measurement)
 *
 * **Usage:**
 * ```kotlin
 * val registry = LayoutRegistry()
 * registry.put(element.id, element, 120f)  // Cache height 120 points
 * val cached = registry.get(element.id, element)  // Returns 120f if unchanged
 * ```
 *
 * ### 4. FlowController
 * Manages pagination rules:
 * - **Widows**: Don't leave a single line at bottom of page
 * - **Orphans**: Don't start a page with a single line from previous page
 * - **Keep With Next**: Keep a heading together with following paragraph
 * - **Atomic Elements**: Never split tables/images
 * - **Page Breaks**: Respect explicit page break markers
 *
 * **Usage:**
 * ```kotlin
 * val flowController = FlowController(textMeasurer, unitConverter)
 * if (flowController.shouldNotSplit(element)) {
 *     // Move entire element to next page
 * }
 * ```
 *
 * ### 5. PrintPaginator
 * The core "Pouring" algorithm that divides content into pages.
 *
 * **Algorithm:**
 * ```
 * 1. Start with Page 1, currentY = marginTop
 * 2. For each element:
 *    a. Measure: Get element height
 *    b. Check: Does it fit on current page?
 *       - YES -> Add to page, move currentY down
 *       - NO:
 *          * If atomic: Move to next page
 *          * If flowable: Split at line boundary
 * 3. Repeat until all elements processed
 * ```
 *
 * **Usage:**
 * ```kotlin
 * val paginator = PrintPaginator(textMeasurer, unitConverter, registry, flowController)
 * val pages = paginator.paginate(
 *     data = documentData,
 *     dimensions = PageDimensions.Letter
 * )
 * ```
 *
 * ### 6. IncrementalPaginator
 * Wrapper around PrintPaginator for efficient updates when content changes.
 *
 * **"Dirty Region" Strategy:**
 * 1. Detect which element changed
 * 2. Re-measure only that element
 * 3. If height unchanged: Stop! No ripple effect
 * 4. If height changed: Re-paginate from that point onwards
 * 5. Find "Settlement Point": where the ripple effect stops
 * 6. Combine: Unchanged pages + Affected pages + Renumbered remaining pages
 *
 * **Performance:**
 * - Editing Page 5 of 100: Only re-paginate ~5-10 pages (not all 100)
 * - Reduces CPU/battery usage during editing
 *
 * **Usage:**
 * ```kotlin
 * val incrementalPaginator = IncrementalPaginator(...)
 * val updatedPages = incrementalPaginator.incrementalUpdate(
 *     previousPages = oldPages,
 *     previousData = oldData,
 *     newData = editedData,
 *     dimensions = PageDimensions.Letter,
 *     changedElementIndex = 10  // Element 10 was edited
 * )
 * ```
 *
 * ## Complete Example Usage
 *
 * ```kotlin
 * // 1. Initialize the factory
 * val factory = PaginationEngineFactory(context)
 * val paginator = factory.createPaginator(useIncrementalUpdates = true)
 *
 * // 2. Load a document
 * val document = DocumentData(
 *     filename = "my_doc.docx",
 *     content = listOf(
 *         DocumentElement.Paragraph(
 *             spans = listOf(
 *                 TextSpan("Hello ", fontSize = 12),
 *                 TextSpan("World", fontSize = 12, isBold = true)
 *             ),
 *             style = ParagraphStyle()
 *         ),
 *         // ... more elements
 *     )
 * )
 *
 * // 3. Paginate
 * val pages = paginator.paginate(
 *     data = document,
 *     dimensions = PageDimensions.Letter
 * )
 *
 * // 4. Render pages
 * for (page in pages) {
 *     println("Page \${page.index + 1}:")
 *     for (positioned in page.elements) {
 *         println("  - Element at (\${positioned.bounds.left}, \${positioned.bounds.top})")
 *     }
 * }
 *
 * // 5. Handle edits (incremental update)
 * val editedDocument = document.copy(
 *     content = document.content.toMutableList().apply {
 *         this[5] = DocumentElement.Paragraph(...)  // Edit element 5
 *     }
 * )
 *
 * val updatedPages = (paginator as IncrementalPaginator).incrementalUpdate(
 *     previousPages = pages,
 *     previousData = document,
 *     newData = editedDocument,
 *     dimensions = PageDimensions.Letter,
 *     changedElementIndex = 5
 * )
 * ```
 *
 * ## Edge Cases Handled
 *
 * 1. **Empty document**: Returns single empty page
 * 2. **Single huge paragraph**: Splits across multiple pages with Widow/Orphan prevention
 * 3. **Images larger than page**: Placed on their own page
 * 4. **Very long tables**: Placed on their own page (doesn't split)
 * 5. **Page breaks**: Respected and enforced
 * 6. **Headings at page boundary**: "Keep With Next" prevents orphan headings
 * 7. **Text measurement caching**: Avoids measuring unchanged content
 * 8. **Floating-point precision**: UnitConverter prevents sub-pixel rounding errors
 *
 * ## Performance Characteristics
 *
 * | Operation | Complexity | Time |
 * |-----------|-----------|------|
 * | measureParagraph | O(text length) | ~1-5ms |
 * | Full pagination | O(n elements * avg measurements) | ~50-200ms |
 * | Incremental update | O(affected pages) | ~5-20ms |
 * | estimatePageCount | O(n elements) | ~20-50ms |
 *
 * ## Debugging & Testing
 *
 * ```kotlin
 * // Get cache statistics
 * val stats = registry.getStats()
 * println("Cache entries: \${stats.totalEntries}, Size: \${stats.cacheSize}")
 *
 * // Verify unit conversions
 * val isValid = unitConverter.verifyRoundTripConversion(100f)
 * println("Conversion valid: \$isValid")
 *
 * // Check element classification
 * val isFlowable = element.isFlowable()
 * println("Element can split: \$isFlowable")
 * ```
 *
 * ## Integration Points
 *
 * The pagination engine integrates with:
 * - **Renderer**: Consumes PageModel to draw pages on screen
 * - **Editor**: Feeds incremental updates for fast re-pagination on edits
 * - **Parser**: Consumes DocumentData from DOCX/ODT parsers
 * - **Storage**: Caches can be serialized for offline access
 *
 * ## Future Enhancements
 *
 * - [ ] Column layout support (newspaper-style)
 * - [ ] Footnote/endnote placement
 * - [ ] Text-wrapping around floating images
 * - [ ] Two-page spread mode (for books)
 * - [ ] Progressive/streaming pagination for huge documents
 * - [ ] Pagination performance profiling UI
 *
 */
class PaginationEngineDocumentation
