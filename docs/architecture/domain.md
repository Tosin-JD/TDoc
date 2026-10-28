# Architecture — Domain Layer

The domain layer is the core of TDoc's pagination and text measurement engine. It is the Android-independent heart of the application.

---

## Module Overview

```
domain/
└── pagination/          ← Pagination engine (the "Brain" of TDoc)
    ├── Paginator.kt            ← Core interface
    ├── PrintPaginator.kt       ← Full pagination algorithm
    ├── IncrementalPaginator.kt ← Dirty-region incremental updates
    ├── LayoutRegistry.kt       ← Cache for measured element heights
    ├── TextMeasurer.kt         ← Text measurement (StaticLayout wrapper)
    ├── UnitConverter.kt        ← PT ↔ PX conversion
    ├── FlowController.kt       ← Pagination rules (widows/orphans, etc.)
    ├── SectionResolver.kt      ← Section dimension resolution
    └── SpacingResolver.kt      ← Paragraph spacing resolution
```

---

## Pagination Engine

The pagination engine is the "brain" of TDoc. It takes a `DocumentData` (a stream of content) and divides it into physical pages.

### PrintPaginator (Full Algorithm)

```
DocumentData
  └─> PrintPaginator.paginate()
       ├─ Resolve section dimensions (apply header/footer reservation)
       ├─ Filter out HeaderFooter, Note, Comment, Bookmark, Field, Metadata
       ├─ Iterate sections and elements
       ├─ Measure each element (from LayoutRegistry cache if available)
       ├─ Check: does it fit on the current page?
       │   ├─ YES → add to page, advance currentY
       │   ├─ NO → if atomic (image, table, page break), move to next page
       │   └─ If flowable (paragraph), split at line boundary
       └─ Attach header/footer elements to pages
```

Key methods:

| Method | Purpose |
|---|---|
| `paginate(data, dimensions)` | Main entry point |
| `measureElement(element, dimensions)` | Get measured height (cached) |
| `addElementToPage(...)` | Place element on a page |
| `splitElement(...)` | Split a paragraph or table across pages |
| `splitParagraphElement(...)` | Split a paragraph at a fitting line boundary |
| `createPage(...)` | Create a `PageModel` from the current state |

### IncrementalPaginator (Dirty Region Strategy)

When a user edits paragraph 10, we do NOT re-paginate all 100 pages:

```
1. Invalidate cache for element 10
2. Measure new height of element 10
3. If height unchanged → return old pages (fast path!)
4. Else → find page containing element 10 (affected page)
5. Re-paginate from affected page onwards
6. Find settlement point (where the ripple stops)
7. Combine: unchanged pages + new pages + renumbered remaining
```

---

## LayoutRegistry (Height Cache)

A key-value cache that stores pre-measured heights for elements:

```kotlin
class LayoutRegistry {
    // elementId -> (contentHash, heightPt)
    private val heightCache = mutableMapOf<String, CachedMeasurement>()
    
    data class CachedMeasurement(
        val contentHash: Long,
        val heightPt: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun put(elementId: String, element: DocumentElement, heightPt: Float)
    fun get(elementId: String, element: DocumentElement): Float?  // null on cache miss or hash mismatch
    fun invalidate(elementId: String)
    fun clear()
}
```

### Content Hash

The cache key uses a **content hash**, not just the element ID:

```kotlin
private fun computeContentHash(element: DocumentElement): Long {
    return when (element) {
        is DocumentElement.Paragraph -> {
            val textContent = element.spans.joinToString("") { it.text }
            val styleHash = element.style.hashCode()
            (textContent.hashCode().toLong() * 31 + styleHash).toLong()
        }
        is DocumentElement.Table -> {
            val content = element.rows.flatten().joinToString("|")
            val metadata = element.metadata.hashCode()
            (content.hashCode().toLong() * 31 + metadata).toLong()
        }
        is DocumentElement.Image -> {
            element.sourceUri.hashCode().toLong()
        }
        // ...
    }
}
```

**Why this matters:** If a paragraph's text changes, the hash changes, the cache entry is invalidated, and the new height is measured. Without this, the paginator would use stale heights and the layout would be wrong.

---

## TextMeasurer

Wraps Android's `StaticLayout` for precise text measurement:

```kotlin
class TextMeasurer(
    private val context: Context,
    private val unitConverter: UnitConverter
) {
    fun measureParagraph(spans, style, widthPt, indent): Float
    fun measurePlainText(text, fontSize, isBold, widthPt): Float
    fun getLastFittingLine(spans, style, widthPt, availableHeightPt, indent): Int
    fun buildParagraphLayout(...): ParagraphLayout?
    fun buildPlainTextLayout(...): PlainTextLayout?
    fun clearCache()
}
```

### Measurement Strategy

1. Measure once using `StaticLayout`
2. Cache in `LayoutRegistry` keyed by `elementId + contentHash`
3. Reuse cached height when content hasn't changed
4. Invalidate cache when element is edited

---

## Design Decisions

| Decision | Rationale |
|---|---|
| Two paginators (full + incremental) | Full pagination is expensive for large documents; incremental is necessary for live editing |
| Content-hash cache (not just ID) | Prevents stale-layout bugs where edited text used old height |
| `LayoutRegistry` shared between engines | Both `PrintPaginator` and `IncrementalPaginator` read from the same cache |
| `StaticLayout` for measurement | Android's hardware-accelerated text measurement is accurate and efficient |
| Immutable `DocumentElement` | Makes pagination deterministic and testable |
