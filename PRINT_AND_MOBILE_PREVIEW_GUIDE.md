# TDoc Preview Mode: Print Layout and Mobile View

This document explains how the TDoc Android app renders a document in **preview mode** for two different surfaces:

1. **Print layout view** — a paginated, page-by-page preview that looks like a printed document.
2. **Mobile view** — a reflowing, scrollable list that reads like a mobile document (document editor).

It also explains how editing and previewing are controlled, how the pagination engine turns a stream of content into physical pages, and how print/export sends those pages to the Android print service.

---

## Table of Contents

1. [The Big Picture](#the-big-picture)
2. [Core Enums: ViewMode and EditorMode](#core-enums-viewmode-and-editormode)
3. [The Document Model](#the-document-model)
4. [Unit System: Points vs Pixels](#unit-system-points-vs-pixels)
5. [The Print Layout Pipeline](#the-print-layout-pipeline)
   - [Phase 1 — Measure every element](#phase-1--measure-every-element)
   - [Phase 2 — Paginate into pages](#phase-2--paginate-into-pages)
   - [Phase 3 — Render pages](#phase-3--render-pages)
   - [What each piece does](#what-each-piece-does)
6. [The Mobile Layout Pipeline](#the-mobile-layout-pipeline)
7. [How the Two Modes Are Wired Together (EditorScreen)](#how-the-two-modes-are-wired-together-editor-screen)
8. [Edit vs Preview](#edit-vs-preview)
9. [Incremental Updates for Performance](#incremental-updates-for-performance)
10. [Print/Export (Android Print Service)](#printexport-android-print-service)
11. [Key Concepts, Explained](#key-concepts-explained)
12. [Adding a New Element Type](#adding-a-new-element-type)
13. [Common Pitfalls](#common-pitfalls)

---

## The Big Picture

TDoc opens a DOCX or ODT file and turns it into a list of `DocumentElement` objects. Those elements are just data — the same list is rendered two different ways depending on the user's choice:

```
Parsed DOCX/ODT
     |
     v
DocumentElement list (flat, in document order)
     |
     +-------------------+-------------------+
     |                                       |
     v                                       v
 MobileView (LazyColumn, reflows)      PrintView (paginated pages)
     |                                       |
     v                                       v
 Scrollable list of paragraphs           Grid of page-sized sheets
```

The **shared brain** behind both is the pagination engine. For print, it decides which elements go on which page. For mobile, no pagination happens — the content just flows.

Whether the user can **type** in the preview depends on `EditorMode`:
- `EDIT` — fields are editable.
- `PREVIEW` — everything is read-only.

---

## Core Enums: ViewMode and EditorMode

These two enums are the switches that control the UI.

### ViewMode

```kotlin
enum class ViewMode {
    MOBILE, PRINT
}
```

- `MOBILE` — renders the document as a scrolling, reflowing list. This is the document-editor experience.
- `PRINT` — renders the document as a series of page-sized sheets with margins, headers, footers, and real page breaks.

`EditorScreen` picks one of these and renders the corresponding layout composable:

```kotlin
when (uiState.viewMode) {
    ViewMode.MOBILE -> MobileLayout(...)
    ViewMode.PRINT  -> PrintLayout(...)
}
```

### EditorMode

```kotlin
enum class EditorMode {
    EDIT, PREVIEW
}
```

- `EDIT` — the document content is editable (text fields, editable headers).
- `PREVIEW` — the document is read-only. Editing controls are hidden.

`EditorScreen` uses `EditorMode` to decide whether to show the edit toolbar, the FAB, and whether content is editable:

```kotlin
if (uiState.editorMode == EditorMode.PREVIEW) {
    // show read-only preview UI (no toolbar, read-only content)
} else {
    // show the editor toolbar and editable fields
}
```

**Key distinction:** `ViewMode` controls *how the document is laid out* (mobile list vs. print pages). `EditorMode` controls *whether the user can edit it* (edit fields vs. read-only preview).

The mode is toggled with a single button in the app bar, and the back button behaves differently depending on the mode:

```kotlin
BackHandler {
    if (uiState.editorMode == EditorMode.EDIT) {
        viewModel.toggleEditorMode()   // EDIT -> PREVIEW
    } else {
        viewModel.closeDocument()       // PREVIEW -> close
    }
}
```

---

## The Document Model

### DocumentElement

Everything in the document is a `DocumentElement`. It is a sealed class (or class hierarchy) of content types:

- `Paragraph` — a block of text with runs/spans
- `SectionHeader` — a heading
- `Section` — page/section setup metadata
- `Table` — a table with rows and cells
- `Image` — an image with a source URI
- `Drawing` — an embedded drawing
- `HeaderFooter` — page header/footer content
- `PageBreak` — a forced page break
- `Note`, `Comment`, `Bookmark`, `Field`, `Metadata`, `EmbeddedObject` — auxiliary/annotation content

Each element has an `id` (used for the layout registry cache and for updating by ID), its content, and styling.

### PageModel

A `PageModel` is a **ready-to-paint snapshot of a single sheet of paper**. It is produced by the pagination engine and consumed by the print renderer.

```kotlin
data class PageModel(
    val index: Int,                          // page number (0-based)
    val dimensions: PageDimensions,          // paper size, margins, body box
    val elements: List<PositionedElement>,   // body content for this page
    val headerElements: List<PositionedElement> = emptyList(),
    val footerElements: List<PositionedElement> = emptyList(),
    val sectionIndex: Int = 0,
    val state: PageState = PageState.CLEAN,
    val stableKey: String = "",              // for Compose list stability
    val documentFingerprint: Long = 0L
)
```

`PageState` is an enum:
- `CLEAN` — ready to render
- `DIRTY` — needs recalculation
- `MEASURING` — currently being processed

### PositionedElement

An element that has been placed on a page has a `PositionedElement`, which wraps the original element with its calculated position:

```kotlin
data class PositionedElement(
    val elementId: String,
    val element: DocumentElement,
    val pageIndex: Int,
    val bounds: RectF,               // left, top, right, bottom in points
    val indentation: ElementIndentation,
    val metadata: Map<String, Any>,
    val layoutResult: LayoutResult
)
```

The `bounds` rectangle is in **points** and is what the renderer uses to draw the element at the correct spot on the page.

### PageDimensions

`PageDimensions` defines the physical page and the area available for body content:

```kotlin
data class PageDimensions(
    val width: Float = 612f,    // Standard Letter (8.5 inches x 72 pt/inch)
    val height: Float = 792f,   // Standard Letter (11 inches x 72 pt/inch)
    val marginLeft: Float = 72f,
    val marginTop: Float = 72f,
    val marginRight: Float = 72f,
    val marginBottom: Float = 72f,
    val headerHeight: Float = 0f,
    val footerHeight: Float = 0f,
    val headerEdgePt: Float = 36f,
    val footerEdgePt: Float = 36f
) {
    val printableWidth: Float get() = width - marginLeft - marginRight
    val printableHeight: Float get() = height - marginTop - marginBottom
    val bodyTop: Float get() = maxOf(marginTop, headerEdgePt + headerHeight)
    val bodyBottom: Float get() = minOf(height - marginBottom, height - footerEdgePt - footerHeight)
    val bodyHeight: Float get() = bodyBottom - bodyTop
    val bodyRect: LayoutRect get() = ...
    val headerRect: LayoutRect get() = ...
    val footerRect: LayoutRect get() = ...
    companion object {
        val Letter = PageDimensions(width = 612f, height = 792f)
        val A4 = PageDimensions(width = 595.27f, height = 841.89f)
        val Default = A4
        val Legal = PageDimensions(width = 612f, height = 1008f)
        val Executive = PageDimensions(width = 540f, height = 720f)
    }
}
```

The default is **A4**; Letter is available as a named constant. The `printableWidth` and `printableHeight` are the area actually available for body content after margins.

---

## Unit System: Points vs Pixels

This is one of the most important concepts for understanding the codebase. The engine works internally in **points** (pt), while Android rendering works in **pixels** (px). The two are bridged by `UnitConverter`.

**Why?**
- Document formats (DOCX/ODT) use **points**: 1 inch = 72 points.
- Android `Canvas` draws in **pixels**: physical pixels on the screen, which depend on the device's density.

**The conversion** (in `UnitConverter`):

```kotlin
// displayMetrics.density: 1.0 for 160dpi, 2.0 for 320dpi, etc.
private val pxPerPt: Float = displayMetrics.density * (160f / 72f)
```

So 1 point = `density * 2.222` pixels (at 160 dpi, 1 pt = 2.222 px).

The converter also rounds to avoid sub-pixel jitter:

```kotlin
fun ptToPx(points: Float): Float {
    val px = points * pxPerPt
    return round(px * 2f) / 2f   // round to nearest 0.5 pixel
}
```

This means: all internal measurements are in points, and every call site that needs to draw on Android converts with `unitConverter.ptToPx(...)`. Never mix the two directly — the `UnitConverter` is the single source of truth.

---

## The Print Layout Pipeline

The print pipeline is a three-phase process that turns a document into a list of pages, then paints those pages.

### Overall Flow

```
DocumentData (content list)
        |
        v
PrintPaginator.paginate(data, dimensions)
        |
        +-- Phase 1: measure every element -> height cache
        |
        +-- Phase 2: paginate into PageModel list
        |         (split tables/paragraphs, handle page breaks, keep-with-next)
        |
        +-- Phase 3: attach header/footer elements
        |
        v
List<PageModel>   -->   rendered by PrintLayout / PageView / PrintElementRenderer
```

---

### Phase 1 — Measure Every Element

Before deciding where anything goes, the engine measures how tall each element is. This is the job of `measureElement` in `PrintPaginator`.

```kotlin
private fun measureElement(element: DocumentElement, dimensions: PageDimensions): Float {
    registry.get(element.id, element)?.let { return it }   // cached?

    val height = when (element) {
        is DocumentElement.Paragraph -> {
            val indent = indentationResolver.resolve(element.style.indentation, element.listInfo)
            val spacing = spacingResolver.resolve(element.style.spacing)
            val textHeight = textMeasurer.measureParagraph(
                element.renderSpans(), element.style, dimensions.printableWidth, indent
            )
            spacing.beforePt + textHeight + spacing.afterPt
        }
        is DocumentElement.SectionHeader -> {
            textMeasurer.measurePlainText(
                element.text, fontSize = headingFontSizeFromStyle(element),
                isBold = true, widthPt = dimensions.printableWidth
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

    registry.put(element.id, element, height)   // cache it
    return height
}
```

**What happens here:**

1. **Check the cache** (`LayoutRegistry`). If this element was measured before and its content hash matches, reuse the cached height. This is a huge win for incremental updates.

2. **Measure by type:**
   - Paragraphs: measure the text (via `TextMeasurer`), then add `spacing.before`, the text height, and `spacing.after`.
   - Headers: measure the text in bold at the heading font size.
   - Tables: build a full table layout first, then measure its total height (minimum 24 pt for the first row).
   - Images: resolve the image layout (scaling), then add caption height if present.
   - Drawings: measure from the EMU dimensions in the OOXML.
   - Page breaks and sections: zero height.

3. **Cache the result** so subsequent passes can reuse it.

**The `LayoutRegistry`** is a cache that maps `elementId` to `{ contentHash, heightPt, timestamp }`. The content hash changes only when the element's content changes, so the cache lets the engine detect edits cheaply. `LayoutRegistry` also classifies each element as `FLOWABLE` (can be split across pages, e.g. paragraph, table, section header), `ATOMIC` (must stay on one page, e.g. image, page break), or `HEADER_FOOTER`.

---

### Text Measurement: TextMeasurer

`TextMeasurer` measures text using Android's low-level `StaticLayout`, which is fast and accurate. It works entirely in points.

```kotlin
fun buildParagraphLayout(
    spans: List<TextSpan>,
    style: ParagraphStyle,
    widthPt: Float,
    indentation: ElementIndentation = ElementIndentation(),
    isDarkMode: Boolean = false
): StaticLayout? {
    ...
    val text = SpannableTextBuilder.build(spans, baseSizePt, fontResolver, tokens, isDarkMode, sizeToPx)
    val paint = createBasePaint(baseSizePt, isDarkMode)
    val lineSpacing = lineSpacingFor(flowResolver.resolveLineHeight(style.spacing), paint)
    val firstLineIndentPx = unitConverter.ptToPx(indentation.firstLinePt - indentation.hangingPt).toInt()
    val restLineIndentPx = unitConverter.ptToPx(indentation.hangingPt).toInt()

    return buildLayout(
        text = text,
        paint = paint,
        widthPt = effectiveWidth,
        alignment = alignmentToLayout(style.alignment),
        lineSpacingPx = lineSpacing.first,
        lineSpacingMultiplier = lineSpacing.second,
        justificationMode = justificationModeFor(style.alignment),
        firstLineIndentPx = firstLineIndentPx,
        restLineIndentPx = restLineIndentPx
    )
}
```

**Why `StaticLayout`?** It is the same primitive Android itself uses to lay out `TextView` content. By using it directly, the engine gets:
- Word wrapping and justification.
- Correct line heights for the given font.
- Indentation and hanging indents.
- Hyphenation support.

The height of the resulting `StaticLayout` is converted back to points with `unitConverter.pxToPt(...)`.

---

### Phase 2 — Paginate into Pages

The core of the pagination engine is `paginateElement` in `PrintPaginator`. It processes elements one at a time, maintaining a running `currentY` position on the current page, and advances to the next page whenever an element doesn't fit.

```kotlin
private fun paginateElement(
    element: DocumentElement,
    elementIndex: Int,
    dimensions: PageDimensions,
    state: PaginationState,
    isDarkMode: Boolean = false
) {
    if (flowController.isPageBreak(element)) {
        if (state.currentPageElements.isNotEmpty()) advanceToNextPage(state)
        else state.currentPageIndex++; state.currentY = dimensions.bodyTop
        return
    }

    if (element is DocumentElement.Paragraph && element.style.pageBreakBefore) {
        if (state.currentPageElements.isNotEmpty()) advanceToNextPage(state)
    }

    var pending: DocumentElement? = element
    while (pending != null) {
        val currentElement = pending
        val elementHeight = measureElement(currentElement, dimensions)
        val availableSpace = dimensions.bodyHeight - (state.currentY - dimensions.bodyTop)

        if (elementHeight <= availableSpace) {
            // Fits on the current page — add it and move on.
            addElementToPage(currentElement, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements, isDarkMode)
            state.currentY += elementHeight
            pending = null
        } else if (flowController.shouldNotSplit(currentElement) ||
            (currentElement is DocumentElement.Paragraph && currentElement.style.keepWithNext) ||
            (elementHeight <= dimensions.bodyHeight && flowController.shouldMoveToOwnPage(elementHeight, dimensions.bodyHeight))) {
            // Atomic, keep-with-next, or too big — move to a fresh page.
            if (state.currentPageElements.isNotEmpty()) advanceToNextPage(state)
            addElementToPage(currentElement, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements, isDarkMode)
            state.currentY += elementHeight
            pending = null
        } else {
            // Split: cut the element at the best point and continue on the next page.
            val (fragment1, fragment2) = splitElement(currentElement, availableSpace, dimensions.printableWidth)
            val firstHeight = fragment1?.let { measureElement(it, dimensions) } ?: 0f
            if (fragment1 != null) {
                addElementToPage(fragment1, state.currentY, state.currentPageIndex, dimensions, state.currentPageElements, isDarkMode)
                state.currentY += firstHeight
            }
            pending = fragment2
            // (handle the "next page" for fragment2 in the rest of the loop)
        }
    }
}
```

**What happens here:**

1. **Forced page breaks:** If the element is a `PageBreak`, or if a paragraph has `pageBreakBefore`, the engine finishes the current page and starts a new one before placing this element.

2. **Fits:** If the element's height is ≤ available space, it is added to the current page at `currentY`, and `currentY` advances by the element's height.

3. **Atomic/keep-with-next/too big:** An image is atomic (never split), a heading may have `keep_with_next` (stay with the next paragraph), or an element could be larger than the whole body. In these cases the engine moves to a fresh page and places it there.

4. **Split:** For a paragraph (or table) that is too tall but could be partially fit, the engine splits it into two fragments: `fragment1` (goes on the current page) and `fragment2` (continues on the next page). Paragraphs are split line-by-line at the last fitting line; tables are split row-by-row.

**Supporting classes in the flow:**

- **`FlowController`** — holds pagination rules:
  - `minLinesBefore` / `minLinesAfter`: widows & orphans prevention (minimum lines at bottom/top of a page).
  - `keepWithNextElements`: a heading stays with the paragraph that follows.
  - `noSplitElements`: images and page breaks are never split.
  - `shouldMoveToOwnPage`: an element taller than 90% of the body goes on its own page.

- **`SectionResolver`** — converts parsed section properties (from DOCX XML, which uses twips) into `PageDimensions`. 1 twip = 1/1440 inch = 0.05 points.

- **`IndentationResolver`** and **`SpacingResolver`** — convert spacing/indentation values (twips in the source) to points.

- **`ImageScaleCalculator`** — scales images to fit the page without distortion:
  - Never upscale (scale factor capped at 1.0).
  - Never distort (preserves aspect ratio).
  - Never overflow (height capped by `maxHeightFraction`, default 0.8 of the body height).

- **`buildTableLayout`** — lays out a table: computes column widths (respecting explicit `gridColumnWidthsPt` if present, otherwise splitting space by content weight), then lays out each row.

- **`LayoutResult`** — a sealed result of measuring/positioning an element, e.g. `TextLayout`, `TableLayout`, `ImageLayout`, `Empty`. The renderer uses this to decide how to draw the element.

---

### Phase 3 — Render Pages

Once pagination is done, the engine produces a list of `PageModel` objects. `PrintLayout` then renders them as a `LazyColumn` of `PageView` items.

**`PrintLayout`** (in `print/PrintLayout.kt`):

```kotlin
@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    // Build the engine components once.
    val unitConverter = remember(context) { UnitConverter(context) }
    val textMeasurer = remember(context, unitConverter) { TextMeasurer(context, unitConverter) }
    val layoutRegistry = remember { LayoutRegistry() }
    val flowController = remember(textMeasurer, unitConverter) { FlowController(textMeasurer, unitConverter) }
    val imageDimensionsProvider = remember { BitmapImageDimensionsProvider() }
    val paginator = remember(...) {
        PrintPaginator(textMeasurer, unitConverter, layoutRegistry, flowController, imageDimensionsProvider)
    }
    val renderer = remember(unitConverter) { PrintElementRenderer(unitConverter) }
    val sectionResolver = remember { SectionResolver() }
    val listState = rememberLazyListState()

    val currentDocument by viewModel.currentDocument.collectAsState()
    val documentData = remember(currentDocument, viewModel.documentElements) {
        currentDocument?.copy(content = viewModel.documentElements)
            ?: DocumentData(content = viewModel.documentElements)
    }

    val pagesState = remember { mutableStateOf<List<PageModel>>(emptyList()) }
    val pages = pagesState.value

    // Run pagination when the document or theme changes.
    LaunchedEffect(documentData, isDarkMode) {
        delay(200)
        val result = withContext(Dispatchers.Default) {
            val sections = documentData.content.filterIsInstance<DocumentElement.Section>()
            val defaultDimensions = if (sections.isNotEmpty()) {
                sectionResolver.resolve(sections.first().properties)
            } else {
                PageDimensions.Default
            }
            paginator.paginate(documentData, defaultDimensions, isDarkMode = isDarkMode)
        }
        // Generate stable keys for compose list items.
        val pagesWithKeys = result.mapIndexed { idx, page ->
            val firstElementId = page.elements.firstOrNull()?.elementId ?: ""
            val contentHash = page.elements.fold(0) { acc, elem -> acc + elem.elementId.hashCode() }
            page.copy(
                stableKey = "$firstElementId_${contentHash}_$idx",
                documentFingerprint = documentData.hashCode().toLong()
            )
        }
        pagesState.value = pagesWithKeys
    }

    // Track scroll position and report current page to the view model.
    LaunchedEffect(pages, listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val mostVisibleItem = layoutInfo.visibleItemsInfo.maxByOrNull { item ->
                (minOf(item.offset + item.size, layoutInfo.viewportEndOffset)
                    - maxOf(item.offset, layoutInfo.viewportStartOffset)).coerceAtLeast(0)
            }
            mostVisibleItem?.index?.plus(1) to pages.size.coerceAtLeast(1)
        }.distinctUntilChanged()
        .collect { (currentPage, pageCount) ->
            viewModel.onPrintPageChanged(currentPage ?: 1, pageCount)
        }
    }

    // Render the page list.
    Box(
        modifier = Modifier.fillMaxSize().background(if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFE4E4E4)),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pages, key = { page -> page.stableKey.ifEmpty { page.index } }) { page ->
                PageView(
                    pageModel = page,
                    pageCount = pages.size,
                    renderer = renderer,
                    unitConverter = unitConverter,
                    viewModel = viewModel,
                    isEditable = isEditable,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}
```

**Key points about `PrintLayout`:**

1. **Engine components are created once** with `remember`, so pagination doesn't rebuild the text measurer or converters on every recomposition.

2. **Pagination runs off the main thread** (`withContext(Dispatchers.Default)`) with a short 200 ms debounce. This keeps the UI responsive while the pages are computed.

3. **Stable keys** are generated for each page so that `LazyColumn` can preserve item state during recomposition. Keys are based on the first element ID + a hash of the content + the page index.

4. **Scroll tracking** uses `snapshotFlow` on the `LazyListState` to report the currently visible page to the view model (`onPrintPageChanged`), which the app bar uses to show "Page 3 of 5".

**`PageView`** (in `print/PageView.kt`) is the composable that draws a single page:

```kotlin
@Composable
fun PageView(
    pageModel: PageModel,
    pageCount: Int,
    renderer: PrintElementRenderer,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    isDarkMode: Boolean = false
) {
    val pageAspectRatio = remember(pageModel.dimensions.width, pageModel.dimensions.height) {
        pageModel.dimensions.width / pageModel.dimensions.height
    }
    val basePageWidthPx = remember(pageModel.dimensions.width, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.width)
    }
    val basePageHeightPx = remember(pageModel.dimensions.height, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.height)
    }

    val renderBlock = remember(pageModel, renderer, isEditable, isDarkMode, unitConverter) {
        { drawScope: DrawScope ->
            with(renderer) {
                with(drawScope) {
                    val pageScale = min(
                        size.width / basePageWidthPx,
                        size.height / basePageHeightPx
                    )

                    withTransform({
                        scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                    }) {
                        pageModel.headerElements.forEach {
                            render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                        }

                        val bodyRect = pageModel.dimensions.bodyRect
                        val left = unitConverter.ptToPx(bodyRect.left)
                        val top = unitConverter.ptToPx(bodyRect.top)
                        val right = unitConverter.ptToPx(bodyRect.right)
                        val bottom = unitConverter.ptToPx(bodyRect.bottom)
                        clipRect(left, top, right, bottom) {
                            pageModel.elements.forEach {
                                render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                            }
                        }

                        pageModel.footerElements.forEach {
                            render(it, renderTextContent = !isEditable, isDarkMode = isDarkMode)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        val pageWidth = minOf(maxWidth, 760.dp)
        val pageHeight = pageWidth / pageAspectRatio
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val pageHeightPx = with(density) { pageHeight.toPx() }
        val displayScale = remember(pageWidthPx, pageHeightPx, basePageWidthPx, basePageHeightPx) {
            min(pageWidthPx / basePageWidthPx, pageHeightPx / basePageHeightPx)
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(pageWidth).aspectRatio(pageAspectRatio),
                color = if (isDarkMode) Color(0xFF202124) else Color.White,
                shape = RectangleShape,
                shadowElevation = 6.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderBlock(this)
                    }
                    if (isEditable) {
                        EditablePrintOverlay(pageElements = pageModel.elements, pageScale = displayScale, unitConverter = unitConverter, viewModel = viewModel)
                    }
                    Text(
                        text = "Page ${pageModel.index + 1} of $pageCount",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(PaddingValues(bottom = 8.dp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
```

**What `PageView` does:**

1. **Scales the page to fit the screen** — `pageScale` is the smaller of (available width / page width) and (available height / page height). The whole page is scaled uniformly with `withTransform { scale(...) }` so the aspect ratio is preserved.

2. **Renders the page in layers:**
   - First, header elements.
   - Then, body elements, clipped to the body rectangle (`clipRect` so nothing leaks into the margin).
   - Finally, footer elements.

3. **Renders the actual page content** via `PrintElementRenderer`, using a `Canvas` and `drawscope` drawing calls.

4. **Adds an editable overlay** when `isEditable` is true — text fields are positioned over the page at the element's calculated coordinates so the user can edit directly on the page.

5. **Displays a page counter** (e.g., "Page 3 of 5") at the bottom.

**`PrintElementRenderer`** (in `core/rendering/PrintElementRenderer.kt`) draws each element to a `DrawScope`:

```kotlin
class PrintElementRenderer(private val unitConverter: UnitConverter) {
    fun DrawScope.render(positionedElement: PositionedElement, renderTextContent: Boolean = true, isDarkMode: Boolean = false) {
        val bounds = positionedElement.bounds
        val x = unitConverter.ptToPx(bounds.left)
        val y = unitConverter.ptToPx(bounds.top)
        val width = unitConverter.ptToPx(bounds.width())
        val height = unitConverter.ptToPx(bounds.height())
        val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light

        when (val element = positionedElement.element) {
            is DocumentElement.Image -> renderImage(element, positionedElement.layoutResult, x, y, width, height, tokens)
            is DocumentElement.Table -> renderTable(element, positionedElement.layoutResult, x, y, width, height, tokens)
            is DocumentElement.HeaderFooter -> {
                if (renderTextContent) renderHeaderFooter(element, positionedElement.layoutResult, x, y, width, height, tokens)
            }
            else -> {
                if (renderTextContent) renderTextOrPlaceholder(positionedElement.layoutResult, x, y, width, height, tokens)
            }
        }
    }
    ...
}
```

**Drawing primitives used:**

- Text: draws the paragraph's `StaticLayout` inside a clipped canvas at the element's pixel coordinates.
- Tables: fills the table background, header row, zebra rows, cell borders, and draws each cell's text layout.
- Images: decodes the bitmap (only bounds for measurement), scales it to the positioned size, centers it horizontally, then draws it. Missing images show a placeholder box.
- Header/footer: draws a separator line and the text.
- Unsupported/missing content: a light placeholder border.

---

## The Mobile Layout Pipeline

Mobile view is much simpler. It is a reflowing list — there are no pages, margins, or page breaks. The content just flows to fit the screen width.

### `MobileLayout` (in `ui/editor/layouts/MobileLayout.kt`)

```kotlin
@Composable
fun MobileLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(focusRequester),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        itemsIndexed(
            items = viewModel.documentElements,
            key = { _, element -> element.id },
            contentType = { _, element -> element::class }
        ) { index, element ->
            DocumentElementRenderer(
                element = element,
                index = index,
                viewModel = viewModel,
                isEditable = isEditable,
                isDarkMode = isDarkMode
            )
        }
    }
}
```

**What it does:**

1. A `LazyColumn` fills the screen with 16 dp horizontal padding and 12 dp vertical padding.
2. It renders all `DocumentElement` items in order, each as its own item. The `key` is the element's own `id`, so the list can preserve state during edits.
3. Each element is dispatched to a specific renderer based on its type.

### `DocumentElementRenderer` (in `ui/editor/renderer/DocumentElementRenderer.kt`)

```kotlin
@Composable
fun DocumentElementRenderer(
    element: DocumentElement,
    index: Int,
    viewModel: EditorViewModel,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    when (element) {
        is DocumentElement.Paragraph -> {
            // Maps paragraph style to a Material typography style and renders
            // either a Text (read-only) or a TextField (editable).
            if (isEditable) {
                TextField(...)
            } else {
                Text(...)
            }
        }
        is DocumentElement.Table -> TableWidget(table = element, isDarkMode = isDarkMode)
        is DocumentElement.Image -> ImageWidget(...)
        is DocumentElement.SectionHeader -> {
            // Heading rendered with appropriate Material typography based on level.
            if (isEditable) TextField(...) else Text(...)
        }
        is DocumentElement.Section, is DocumentElement.HeaderFooter,
        is DocumentElement.Comment, is DocumentElement.Bookmark,
        is DocumentElement.Field, is DocumentElement.Metadata,
        is DocumentElement.Note, is DocumentElement.PageBreak -> Unit
        is DocumentElement.Drawing -> UnsupportedPlaceholder(...)
        is DocumentElement.EmbeddedObject -> UnsupportedPlaceholder(...)
    }
}
```

**Key points:**

- `Paragraph`, `SectionHeader`, and `Table` and `Image` have real renderers.
- `Section` and `PageBreak` are control data for print layout, so they produce nothing in mobile view (they don't leak into the reading surface — this is an intentional fix referenced in `TODO12.md`).
- `Drawing` and `EmbeddedObject` produce an `UnsupportedPlaceholder` so the user knows something is there but can't be rendered.
- In edit mode, paragraphs and headers are `TextField`s; in preview, they are plain `Text`.

---

## How the Two Modes Are Wired Together (EditorScreen)

`EditorScreen` is the entry point. It contains the app bar, the floating action button, and a `when` statement that selects the layout.

```kotlin
Box(
    modifier = Modifier.weight(1f).fillMaxWidth()
) {
    when (uiState.viewMode) {
        ViewMode.MOBILE -> MobileLayout(
            viewModel = viewModel,
            focusRequester = focusRequester,
            isEditable = uiState.editorMode == EditorMode.EDIT,
            isDarkMode = uiState.isDarkMode
        )
        ViewMode.PRINT -> PrintLayout(
            viewModel = viewModel,
            focusRequester = focusRequester,
            isEditable = uiState.editorMode == EditorMode.EDIT,
            isDarkMode = uiState.isDarkMode
        )
    }

    if (uiState.isLoading || uiState.isSaving) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    if (uiState.editorMode == EditorMode.PREVIEW) {
        DiagnosticsSummaryChip(summary = uiState.diagnostics, onClick = { showDiagnostics = true })
    }
}
```

`isEditable` is `true` only in `EDIT` mode. In `PREVIEW` mode, `PrintLayout` and `MobileLayout` render read-only content.

`EditorScreen` also handles:
- **Back button**: EDIT → switch to PREVIEW; PREVIEW → close document.
- **Top app bar**: shows document title in preview, page counter pill for print view.
- **Floating action button**: visible in PREVIEW, doubles as an "edit" button to switch back to `EDIT` mode.
- **Diagnostics chip**: shown in preview mode, taps to open a diagnostics sheet.

---

## Edit vs Preview

The same rendering composables are used for both edit and preview — the difference is a simple flag.

- **`isEditable = false`** (preview): content is rendered as read-only (`Text`, no text field). In print view, the editable overlay is not drawn.
- **`isEditable = true`** (edit): paragraphs become `TextField` composables, headers become editable text fields, and print view draws an `EditablePrintOverlay` with positioned text fields.

Every edit call flows back to the `EditorViewModel`:

```kotlin
// PrintElementRenderer's EditablePrintOverlay calls:
onValueChange = { viewModel.updateParagraphById(positionedElement.elementId, it) }
```

The view model updates the `documentElements` list, which triggers recomposition of the layout, and the pagination engine re-runs (or uses incremental updates — see below) to refresh the page list.

---

## Incremental Updates for Performance

Re-paginating an entire document on every keystroke would be slow. The app uses `IncrementalPaginator` to re-paginate only what changes.

**Problem:** If a user edits page 5 of a 100-page document, re-paginating all 100 pages on every keystroke causes lag.

**Solution — the dirty-region strategy:**

```kotlin
fun incrementalUpdate(
    previousPages: List<PageModel>,
    previousData: DocumentData,
    newData: DocumentData,
    dimensions: PageDimensions,
    changedElementIndex: Int
): List<PageModel> {
    // Step 1: Measure the changed element. If its height didn't change,
    // return the previous pages unchanged (no ripple effect).
    val previousHeight = previousElement?.let { registry.get(it.id, it) }
    val newHeight = registry.get(changedElement.id, changedElement) ?: measureElement(...)
    if (previousHeight != null && previousHeight == newHeight) {
        return previousPages
    }

    // Step 2: Find which page contains the changed element.
    val affectedPageIndex = findPageContainingElement(previousPages, changedElement.id)

    // Step 3: Re-paginate from that element onwards.
    val repaginatedPages = printPaginator.paginate(newData, dimensions, changedElementIndex)

    // Step 4: Find the "settlement point" — where the ripple stops.
    val settledPageIndex = findSettlementPoint(previousPages.drop(affectedPageIndex), repaginatedPages, affectedPageIndex, dimensions)

    // Step 5: Combine unchanged pages + new pages + renumbered remaining pages.
    val result = mutableListOf<PageModel>()
    result.addAll(previousPages.take(affectedPageIndex))
    result.addAll(repaginatedPages.take(settledPageIndex - affectedPageIndex + 1))
    // renumber remaining pages...
    return result
}
```

**The four steps:**

1. **Check if height changed.** If the edited element has the same height (e.g., a typo that's later fixed), there is no ripple — return the old pages.
2. **Find the affected page.** Locate the page that contains the element that changed.
3. **Re-paginate from there.** `printPaginator.paginate(newData, dimensions, changedElementIndex)` starts paginating from that element index, reusing the unchanged prefix.
4. **Find the settlement point.** Compare old and new pages after the affected page to find where the layout stabilizes, then renumber any pages that shifted.

This means a small edit that doesn't change page count is nearly instant, and even a large edit only re-paginates the affected region, not the whole document.

---

## Print/Export (Android Print Service)

When the user prints or shares the document, the app uses Android's `PrintDocumentAdapter` to write a PDF. The pages are the same `PageModel` list produced by the pagination engine.

**`DocumentPrintAdapter`** (in `ui/print/DocumentPrintAdapter.kt`):

```kotlin
class DocumentPrintAdapter(
    private val context: Context,
    private val pages: List<PageModel>
) : PrintDocumentAdapter() {

    private var printedDocument: PrintedPdfDocument? = null
    private val unitConverter = UnitConverter(context)

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        if (pages.isEmpty()) {
            callback.onLayoutFailed("No pages to print")
            return
        }
        val info = PrintDocumentInfo.Builder("document.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages.size)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        try {
            val document = PrintedPdfDocument(context, PrintAttributes.Builder().build())
            printedDocument = document
            pages.forEachIndexed { index, page ->
                val pdfPage = document.startPage(index)
                // Basic stub: actual rendering would require drawing the PageModel to canvas.
                document.finishPage(pdfPage)
            }
            val out = FileOutputStream(destination.fileDescriptor)
            document.writeTo(out)
            out.close()
            document.close()
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
```

**`PrintHelper`** puts it all together:

```kotlin
class PrintHelper(private val context: Context) {
    fun printDocument(jobName: String, pages: List<PageModel>, attributes: PrintAttributes? = null) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = DocumentPrintAdapter(context, pages)
        val attrs = attributes ?: PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, adapter, attrs)
    }
}
```

**How it works:**
1. `PrintHelper` gets the Android print service and builds a `DocumentPrintAdapter` with the paginated pages.
2. The system calls `onLayout` to confirm the page count.
3. The system calls `onWrite` to write the actual PDF. Each page is started, finished, and the document is written to the output stream.

**Note about `PdfExporter`:** There is also a `PdfExporter` class that creates a `PdfDocument` directly (not through the Android print service). The current implementation is a minimal stub that creates a valid PDF structure but doesn't actually draw the content. The Android print service path (`DocumentPrintAdapter`) is the production print path.

---

## Key Concepts, Explained

### Reflow vs Pagination

- **Reflow (mobile):** Content flows to fit the viewport. Paragraphs wrap naturally. There are no physical pages. This is how a website or a mobile document reader works.
- **Pagination (print):** Content is divided into fixed-size physical sheets. Page breaks, margins, headers, and footers are all determined by the page size and the content's measured height.

Both use the same underlying element model and the same text measurement (`StaticLayout`), which is why the print and mobile views stay visually consistent.

### Points vs Pixels (again)

All layout math (`TextMeasurer`, `PrintPaginator`, `LayoutRegistry`) works in **points**. All drawing (`PageView`'s `Canvas`, `PrintElementRenderer`) works in **pixels** via `UnitConverter.ptToPx()`. If you add a new measurement path, always convert, never mix.

### Stable keys and list state

`PrintLayout` generates `stableKey` values for each page (first element ID + content hash + index). This is what lets `LazyColumn` keep its scroll position and item state when the document changes. Without stable keys, every recomposition would reset the user's scroll position in the print preview.

### The layout registry as a performance cache

`LayoutRegistry` stores the measured height of every element, keyed by element ID, with a content hash to detect changes. It also classifies elements as flowable or atomic. This single cache is what makes both incremental pagination and `keep_with_next` possible without re-measuring the whole document.

### Widows and orphans

`FlowController` enforces typographic rules:
- **Widow:** don't leave a single line of a paragraph at the bottom of a page.
- **Orphan:** don't start a page with just a few lines of a paragraph.
- **keep_with_next:** a heading stays on the same page as the paragraph that follows it.

### Atomic vs flowable elements

- **Flowable:** can be split across pages (paragraph, table, section).
- **Atomic:** must stay on one page (image, page break).
- **Header/footer:** are attached to a page, not paginated.

### The 200 ms debounce on pagination

`PrintLayout` runs pagination in a `LaunchedEffect` with `delay(200)`. This prevents pagination from re-running on every recomposition (e.g., when the user toggles dark mode or a theme changes), and also smooths out the first paint when the document is loaded.

---

## Adding a New Element Type

If the parser produces a new `DocumentElement` type, here's what you need to do to make it render:

### 1. Add the class

Create a new subclass of `DocumentElement` (or extend the sealed class) with its content and styling.

### 2. Measure it (print pagination)

In `PrintPaginator.measureElement`, add a branch:

```kotlin
is MyNewElement -> measureMyElement(element, dimensions)
```

Implement `measureMyElement` to return the element's height in points. Also add it to `LayoutRegistry.classifyElement` as `FLOWABLE` or `ATOMIC`.

### 3. Render it (print)

In `PrintElementRenderer.render`, add a branch to draw the element on the canvas. Use `unitConverter.ptToPx` for all coordinates.

### 4. Render it (mobile)

In `DocumentElementRenderer`, add a branch to show the element in the mobile list. Use `Text` or a custom composable.

### 5. Update the editable overlay

In `PageView`'s `EditablePrintOverlay`, handle the new element for in-place editing if it should be editable in print preview.

### 6. Update `UnsupportedPlaceholder` cases

If the element is meaningful but unsupported, add it there instead of silently dropping it.

---

## Common Pitfalls

1. **Using pixels instead of points in layout math.** Never calculate a position with px values in `PrintPaginator` or `TextMeasurer`. Convert at the boundary with `UnitConverter`.

2. **Forgetting to invalidate the layout cache.** If you add an element type that can change height, update `LayoutRegistry.computeContentHash` so edits invalidate the cache. Otherwise you'll get stale heights.

3. **Splitting tables incorrectly.** Tables should be split row by row, not line by line. `splitTableElement` handles this, but adding a new table feature might break the row boundary logic.

4. **Forgetting `stableKey` on new pages/items.** If you add items to the `LazyColumn` that can change order or count, give them stable keys or the list will reset scroll position on every recomposition.

5. **Drawing a `StaticLayout` without clipping.** Text must be clipped to the element's bounds — otherwise it will bleed into adjacent elements. `PrintElementRenderer`'s `renderTextOrPlaceholder` does exactly this.

6. **Hardcoding page dimensions.** Prefer the `PageDimensions.Default` (A4) or resolve from `SectionResolver`. If you hardcode 612x792 (Letter), A4 documents will render wrong, and vice versa.

7. **Forgetting dark mode.** `PrintElementRenderer` and `DocumentTextStyleMapper` take an explicit `isDarkMode` parameter and map to `DocumentColorTokens.Dark` or `.Light`. Compose renderers (`DocumentElementRenderer`, `TableWidget`, `DocumentImage`) read the resolved `LocalAppIsDarkTheme` instead of taking a parameter, and print layouts receive the same resolved value from `EditorScreen`. If you add a new drawable or text color, respect that one value — never a document-local flag.

8. **Not testing with images.** Images are `ATOMIC` and have a separate scaling path (`ImageScaleCalculator`). A very tall image, or an image with missing intrinsic dimensions, goes through a fallback path. Test with both real and placeholder images.

9. **Re-paginating on every recomposition in `PrintLayout`.** The `LaunchedEffect` + `delay(200)` + `withContext(Dispatchers.Default)` combination is intentional. Don't remove it.

10. **Forgetting `Dispatchers.Default` on pagination.** Pagination is CPU-heavy (layout, measurement, text shaping). It must run off the main thread, or the UI will freeze during large documents.

---

## File Map

| File | Role |
|---|---|
| `ui/editor/EditorScreen.kt` | UI entry point; switches between Mobile/Print view, Edit/Preview mode. |
| `ui/editor/EditorViewModel.kt` | Document state, editing, and view model logic. |
| `ui/editor/layouts/MobileLayout.kt` | Mobile view: reflowing `LazyColumn` of elements. |
| `ui/editor/layouts/print/PrintLayout.kt` | Print view: paginated `LazyColumn` of pages. |
| `ui/editor/layouts/print/PageView.kt` | Single page composable: scales a page to fit the screen and draws it. |
| `ui/editor/layouts/print/PageScaler.kt` | Scale calculation helper (base + fit + user scale). |
| `ui/editor/renderer/DocumentElementRenderer.kt` | Mobile view: dispatches each element to its renderer. |
| `core/rendering/PrintElementRenderer.kt` | Print view: draws each element to a `DrawScope`. |
| `domain/pagination/PrintPaginator.kt` | The pagination engine: measures, splits, and places elements onto pages. |
| `domain/pagination/IncrementalPaginator.kt` | Dirty-region re-pagination for performance. |
| `domain/pagination/Paginator.kt` | Interface the engine implements. |
| `domain/pagination/TextMeasurer.kt` | Measures text with `StaticLayout`; converts px↔pt. |
| `domain/pagination/UnitConverter.kt` | Points↔pixels converter. |
| `domain/pagination/LayoutRegistry.kt` | Height cache and flowable/atomic classification. |
| `domain/pagination/FlowController.kt` | Widows/orphans, keep-with-next, atomic rules. |
| `domain/pagination/SectionResolver.kt` | Parsed section properties → `PageDimensions`. |
| `domain/pagination/ImageScaleCalculator.kt` | Image scaling without distortion. |
| `domain/pagination/ImageDimensionsProvider.kt` | Decodes image bounds only (no full bitmap decode). |
| `domain/model/layout/PageModel.kt` | Ready-to-paint sheet of paper. |
| `domain/model/layout/PageDimensions.kt` | Page geometry (size, margins, body box). |
| `ui/print/PdfExporter.kt` | Direct PDF export class (currently a stub). |
| `ui/print/PrintHelper.kt` | Wires the Android print service to `DocumentPrintAdapter`. |
| `ui/print/DocumentPrintAdapter.kt` | `PrintDocumentAdapter` implementation for the Android print service. |

---

## One-Line Summary

The print and mobile previews share the same parsed document model; the pagination engine (`PrintPaginator`) turns it into pages for print by measuring, splitting, and positioning every element, `PageView`/`PrintElementRenderer` render each page on a scaled canvas, while `MobileLayout`/`DocumentElementRenderer` render the same elements as a reflowing scrollable list, all controlled by `ViewMode` (mobile vs print) and `EditorMode` (edit vs preview), with `IncrementalPaginator` re-paginating only the changed region for performance and `DocumentPrintAdapter` handing the pages to Android's print service for PDF export.
