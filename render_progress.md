# Render Progress — WYSIWYG page rendering checklist

Purpose: a single checklist of everything needed to render DOCX/ODT documents
WYSIWYG — correct page count, page geometry (width/height/margins), font size,
font style, spacing and every other layout-relevant element — with the current
status marked, what remains, and the recommended order to approach it.

Status derived from the actual implementation of the rendering pipeline:

```
ParseResult.content (LegacyElementAdapter)
  → EditorViewModel.documentElements
  → PrintLayout ── PrintPaginator (domain/pagination) ── TextMeasurer (StaticLayout)
  → PageView (Canvas) ── PrintElementRenderer
```

Companion file: `parser_progress.md` (element *parsing* coverage). Items marked
"(parser)" there block rendering here — fix the parser first.

## Legend

| Mark | Meaning |
|------|---------|
| ✅ | Implemented and wired into the render path |
| 🟡 | Partially implemented — the note says what is missing |
| ❌ | Not implemented |
| ➖ | Out of scope by requirement (kept for completeness) |

---

## Overall progress

**Combined: 54 ✅ / 17 🟡 / 58 ❌ out of 129 applicable entries (~42 % complete, ≈ 49 % counting partials).**

| Area | ✅ | 🟡 | ❌ | Rows | Highlights |
|---|---|---|---|---|---|
| A. Page geometry | 3 | 2 | 5 | 10 | Engine ready, but **document page setup never reaches it** (R1) |
| B. Pagination & page count | 10 | 2 | 7 | 19 | Engine strong; count not exposed, inline page breaks lost |
| C. Typography | 5 | 3 | 6 (+1 ➖) | 15 | Sizes/styles work; half-points, caps, hidden text missing |
| D. Paragraph layout | 5 | 1 | 5 | 11 | First-line/hanging indents not applied |
| E. Lists | 2 | 1 | 2 | 5 | Print view draws no bullets/numbers |
| F. Tables | 3 | 1 | 9 | 13 | Weakest area: merges, grid, cell styles all missing |
| G. Images & shapes | 5 | 3 | 4 | 12 | Inline images good; floating/wrap/rotation missing |
| H. Headers & footers | 4 | 1 | 4 | 9 | Attach + reserve works; rich content & page numbers missing |
| I. Non-content flow | 2 | 0 | 3 | 5 | Annotation stubs pollute page 1 |
| J. Page view UI | 4 | 1 | 5 | 10 | No zoom, no "Page x of Y", no current-page tracking |
| K. Edit fidelity | 2 | 1 | 3 | 6 | Edits strip run formatting |
| L. Infrastructure | 6 | 1 | 1 | 8 | Strong: caches, unit conversion, shared math |
| M. Tests & verification | 3 | 0 | 4 | 7 | No end-to-end pagination test yet |

Remaining work items are consolidated as **R1–R28** below, then ordered by
recommended precedence in *Recommended order of precedence*. Parser-side
blockers are cross-referenced to **F1–F28** in `parser_progress.md`.

---

## A. Page geometry (width, height, margins, orientation)

| Element / feature | Status | Notes |
|---|---|---|
| Standard page boxes: Letter (612×792pt) and A4 (595×842pt) defaults | ✅ | `PageDimensions.Letter` / `.A4` |
| twips → points conversion for page size and margins | ✅ | `SectionResolver` (1 twip = 0.05pt), tested (`SectionResolverTest`) |
| Margins applied as the real content box (body rect) | ✅ | `PageDimensions.bodyRect`, tested (`PageDimensionsTest`) |
| **Document's own page size/margins actually driving layout** | ❌ | **Critical:** the rich parse path never emits `DocumentElement.Section` (parsers put sections in `ParsedDocument.sections`; `LegacyElementAdapter` only projects `BlockNode.SectionBreak`, which no parser creates). `PrintLayout` therefore always falls back to A4 + 72pt margins regardless of the file. |
| Landscape/portrait orientation | 🟡 | `pgSz w/h` used directly (Word stores oriented dims, so DOCX landscape works *if* geometry is wired); ODF `print-orientation` parsed into `PageStyle` but never reaches the renderer (same Section-projection gap) |
| Header/footer distance from page edge (`pgMar header/footer`) | ❌ | Parsed as `PageStyle.headerPoints/footerPoints`; `SectionResolver`/`EdgeInsets` ignore it — header sits at `marginTop` |
| Gutter margin | ❌ | Parsed (`gutterPoints`), never applied |
| Multi-column page layout (`w:cols num>1`) | ❌ | `columnCount` parsed; paginator is strictly single-column |
| Page background color/image | ❌ | Paper is always a fixed Surface color |
| Per-section geometry switch mid-document | 🟡 | `PrintPaginator` handles `DocumentElement.Section` (flushes page, applies new dims) but the elements never arrive (see row 4) — effectively dead code today |

## B. Pagination engine & page count

| Element / feature | Status | Notes |
|---|---|---|
| Page-by-page pagination with positioned elements | ✅ | `PrintPaginator.paginate` — greedy fill, `PositionedElement` bounds in points |
| Text measurement identical between measure and draw | ✅ | Both use `StaticLayout` via `TextMeasurer` → page count is self-consistent on a given device |
| Paragraph splitting across page boundaries | ✅ | Line-level split via `getLastFittingLine` + span splitting at char offset |
| Table splitting with header-row repeat on continuation | ✅ | `splitTableElement` re-adds the header row |
| Oversized block moved to its own page | ✅ | `FlowController.shouldMoveToOwnPage` (>90% of body), tested |
| Atomic elements never split (images, page breaks) | ✅ | `FlowController.shouldNotSplit`, tested |
| Forced page break via `DocumentElement.PageBreak` | ✅ | Consumed as a break, block not placed on sheet |
| Empty document → exactly one page | ✅ | `createEmptyPage` |
| Widow/orphan line rules at split points | ✅ | `FlowController.adjustSplitPointForRules`, tested |
| **Inline page breaks (`w:br type="page"`, column breaks)** | ❌ | `LegacyElementAdapter` maps `InlineNode.Break(PAGE/COLUMN)` to an **empty text span**, not `DocumentElement.PageBreak` — Word documents with explicit page breaks paginate wrong. (ODT block `text:soft-page-break` works.) |
| `pageBreakBefore` paragraph property | ❌ | Not in the legacy `ParagraphStyle`; not honored by paginator |
| Keep-with-next (heading never orphaned at page bottom) | ❌ | `FlowController.shouldKeepWithNext` exists but `PrintPaginator` never calls it; property not in legacy model |
| Keep-lines-together / keep-together | ❌ | Parsed (rich model) but not projected, not honored |
| **Page count computed (`pages.size`)** | ✅ | Produced inside `PrintLayout.produceState` |
| **Page count exposed to UI/state** | ❌ | Not in `EditorUiState`; no "Page x of Y" anywhere (only the raw `pages` list is rendered) |
| Fast page-count estimate without full layout | 🟡 | `Paginator.estimatePageCount` implemented but never called by the UI |
| Current-page tracking while scrolling | ❌ | No `currentPage` state, no page indicator pill |
| Incremental re-pagination on edit | 🟡 | `IncrementalPaginator` fully implemented but `PrintLayout` constructs a plain `PrintPaginator` — factory unused |
| Focus/scroll-to-page after edit | ❌ | `focusRequester` passed into `PrintLayout` but never used |

## C. Typography (font size, style, weight, color)

| Element / feature | Status | Notes |
|---|---|---|
| Per-run font size applied to measurement and drawing | 🟡 | Works, but `LegacyElementAdapter` rounds sizes with `Math.round` to whole points — 10.5pt (common in Word) becomes 11pt. Should keep half-points (Float) |
| Bold / italic per run | ✅ | `SpannableTextBuilder` typeface span; `AndroidTypefaceMapper` caches |
| Underline / strikethrough per run | ✅ | `UnderlineSpan` / `StrikethroughSpan` |
| Text color per run | ✅ | `ForegroundColorSpan`; `DocumentTextColorResolver` with dark-mode fallback |
| Highlight (background color) per run | ✅ | `BackgroundColorSpan` |
| Letter spacing (tracking) per run | ✅ | `LetterSpacingSpan` in em, metric-affecting |
| Font family by document font name | 🟡 | Mapped to 4 generic families (serif/sans/mono/cursive) via `FontFamilyResolver` — no bundled fonts, so Calibri vs Arial metrics differ from Word. Unknown names fall back safely (tested) |
| Heading font sizes from document styles | 🟡 | Heuristic `12 + (4 − level)` pt bold; the parsed style sizes are lost when `Heading` → `SectionHeader(text, level)` |
| Half-point font sizes preserved | ❌ | See per-run size note above — `TextSpan.fontSize` is `Int` |
| Small caps / all-caps transform | ❌ | Parsed but never applied at render time |
| Hidden text (`w:vanish`) excluded from layout | ❌ | `isHidden` parsed; builder still appends the text |
| Kerning | ❌ | Parsed, not applied |
| Language / RTL paragraph direction | ❌ | Parsed, not applied |
| Theme colors (`themeColor` → theme1.xml palette) | ❌ | Parser gap (parser_progress D) — runs fall back to black |
| Superscript / subscript | ➖ | Already applied in both render paths; explicitly out of scope per requirements |

## D. Paragraph layout

| Element / feature | Status | Notes |
|---|---|---|
| Alignment: start/end/center/justify/distribute | ✅ | `JUSTIFICATION_MODE_INTER_WORD` for justified/distributed (tested `TextLayoutDecisionsTest`) |
| Left/right indentation | ✅ | `IndentationResolver`, tested |
| List-level indent (0.5"/level) | ✅ | +36pt per level, tested |
| Line spacing: auto (240ths), exact, at-least | ✅ | `TextFlowResolver` shared by Compose + Canvas paths, tested |
| Anti-clipping line-height floor (tallest run never clipped) | ✅ | `MIN_LINE_RATIO = 1.25`, tested |
| Space before / space after | 🟡 | Included in the element's height, but the text layout is drawn at `bounds.top` — before-spacing renders *after* the paragraph. Total page fill is right; per-paragraph rhythm is shifted |
| First-line indent | ❌ | Resolved into `ElementIndentation.firstLinePt` but `TextMeasurer` only subtracts left/right — `StaticLayout` never indents the first line |
| Hanging indent | ❌ | Same — `hangingPt` resolved but unused |
| Tab stops (custom positions, leaders) | ❌ | Only platform default tab stops |
| Paragraph borders (`w:pBdr`) | ❌ | Parsed (rich model) but not projected into legacy `ParagraphStyle`, not drawn |
| Paragraph shading (`w:shd`) | ❌ | Same |

## E. Lists

| Element / feature | Status | Notes |
|---|---|---|
| List label (bullet/number) in **mobile** view | ✅ | `DocumentTextStyleMapper` prepends `listLabel` |
| List label in **print/WYSIWYG** view | ❌ | `TextMeasurer.buildParagraphLayout` builds only from `spans` — bullets/numbers are **invisible in print preview** (measurement matches, but nothing is drawn). The edit overlay does prepend it, so preview and edit disagree |
| Marker text (bullet glyph, "1.") supplied by parser | ✅ | numbering resolver (see parser_progress F) |
| Sequential numbering per item (1, 2, 3…) | ❌ | Parser gap F2 — every item gets the start value |
| Nested list structure/hierarchy | 🟡 | Indent rendered; DOCX nesting flattened at parse (parser F3) |

## F. Tables

| Element / feature | Status | Notes |
|---|---|---|
| Grid lines, outer border, header background, zebra striping | ✅ | `PrintElementRenderer.renderTable`; fallback grid if no layout |
| Table split across pages with header repeat | ✅ | See B |
| Header-row bold | ✅ | `isBold` on first row when `hasHeader` |
| Column widths from document grid (`w:tblGrid`) | ❌ | Estimated from longest-cell text heuristic; parsed grid never reaches the legacy model |
| Cell merge rendering (`gridSpan`, `vMerge`, `hMerge`) | ❌ | Captured in `TableMetadata` but the renderer iterates cells positionally — merged cells render as separate cells |
| Document border styles/colors/shading | ❌ | Renderer uses app design tokens (`DocumentColorTokens`), not the document's `tblBorders`/`shd` |
| Row heights from document (`w:trHeight`) | ❌ | Computed from content, min 24pt |
| Cell margins/padding from document | ❌ | Hardcoded 4pt |
| Per-cell run formatting (fonts, sizes, colors in cells) | ❌ | `LegacyElementAdapter.table()` flattens cells to plain text; fixed 9pt |
| Cell horizontal/vertical alignment from document | ❌ | Plain layout `ALIGN_NORMAL`, drawn at cell top |
| Table width/alignment from document (`tblW`, `jc`) | 🟡 | Always fills printable width, always starts at left margin |
| Nested tables | ❌ | Flattened to text by `plainText()` |
| Table caption/description rendering | ❌ | Stored in metadata only |

## G. Images, drawings & shapes

| Element / feature | Status | Notes |
|---|---|---|
| Embedded images decoded and drawn at intrinsic size | ✅ | Resources materialized to cache files; `BitmapFactory` draw |
| Never upscale / never distort / height cap | ✅ | `ImageScaleCalculator`, tested (9 cases) |
| Intrinsic size (px → pt at 0.75) for layout | ✅ | `BitmapImageDimensionsProvider` (bounds-only decode) |
| Alt text + caption under image | ✅ | Caption laid out + drawn; shared `ImageLayoutConfig` |
| Missing-image placeholder box | ✅ | Crossed rectangle, never silent |
| Horizontal placement of image | 🟡 | Always centered in the body column; Word places inline images at the current text position |
| Floating/anchored images with text wrap | ❌ | Every image is a block-level flow item; `FrameStyle.wrapStyle/positionX/Y` never populated or honored (parser F14) |
| Image rotation | ❌ | Parsed into `FrameStyle.rotationDegrees`, renderer ignores it |
| Drawing/shape extent (size of non-image drawings) | 🟡 | `measureDrawing` reads `widthEmu/heightEmu`, but `LegacyElementAdapter` never fills them → default 200pt box + text placeholder |
| Text boxes (`w:txbxContent`, `draw:text-box`) | ❌ | Inner blocks inlined into flow; no box, no position (parser F7/F8) |
| Charts / SmartArt / OLE objects | ❌ | Placeholder or text stub (parser F23–F25) |
| VML legacy pictures | 🟡 | Parsed as images (parser ✅); rendering works only after resource materialization |

## H. Headers & footers

| Element / feature | Status | Notes |
|---|---|---|
| Header/footer stories attached to every page | ✅ | `PrintPaginator.attachHeaderFooter` |
| Body area reserved so content never overlaps | ✅ | Header/footer height subtracted from `bodyHeight` |
| Variant selection: default / first-page / even-odd | ✅ | `resolveHeaderForPage` (first page of section, `pageIndex % 2`) |
| Header/footer separator line drawn | ✅ | Thin rule under header / above footer |
| Header/footer distance from page edge | ❌ | Reservation measured from text, header anchored at `marginTop` (see A) |
| Rich header/footer content (tables, images, styled runs) | ❌ | `LegacyElementAdapter` flattens stories to plain text; rendered at hardcoded 10pt |
| Section-scoped header/footer assignment | 🟡 | Deduped globally; first/even variants resolved document-wide, not per section reference |
| Field evaluation in headers/footers (PAGE, NUMPAGES, STYLEREF) | ❌ | Shows raw field text / placeholder (parser F19) |
| Footer page-number rendering ("Page 3 of 10") | ❌ | Not present anywhere |

## I. Non-content elements in the flow

| Element / feature | Status | Notes |
|---|---|---|
| Header/footer elements excluded from body flow | ✅ | Filtered before pagination |
| Metadata / comments / notes / bookmarks / fields excluded from print flow | ❌ | `LegacyElementAdapter` puts a Metadata line, comment lines and note lines **before the document body**; `PrintPaginator` lays them out as ~10pt text — page 1 of every WYSIWYG preview starts with "DOCX Metadata: …" and "comment by …" rows that are not in the real document |
| Notes rendered in a footnote area at page bottom | ❌ | Rendered as in-flow text lines (positioned at document top today) |
| Comments shown as annotations (margin/balloon), not body text | ❌ | Same |
| Unsupported/opaque constructs shown as labeled placeholder | ✅ | `EmbeddedObject` → "Unsupported: …" surface in mobile; text stub in print |

## J. Page view UI (the sheet itself)

| Element / feature | Status | Notes |
|---|---|---|
| Correct aspect-ratio page sheet with shadow | ✅ | `PageView` `aspectRatio(width/height)` |
| Content clipped to the body rectangle | ✅ | `clipRect(bodyRect)` around body elements; headers/footers outside clip |
| pt → px conversion with sub-pixel jitter prevention | ✅ | `UnitConverter` rounds to 0.5px, round-trip tested |
| Lazy list of pages | ✅ | `LazyColumn` keyed by page index |
| Zoom / pinch-to-zoom | ❌ | Fixed `PRINT_PREVIEW_SCALE = 0.82f` + 760.dp width cap |
| Actual-size (100%) view mode | ❌ | No way to view at true print scale |
| "Page x of Y" indicator | ❌ | — |
| Scroll-position → current page reporting | ❌ | — |
| Dark mode paper/ink | 🟡 | Paper turns dark (`0xFF202124`) instead of staying white with black ink; text color adjusted for readability. Decide: true WYSIWYG wants white paper always |
| Page footers drawn on the sheet | ❌ | No "Page n of N" footer on sheets |

## K. Edit-mode fidelity

| Element / feature | Status | Notes |
|---|---|---|
| Edit overlay positioned from measured bounds | ✅ | `EditablePrintOverlay` offsets TextField by `bounds × scale` |
| Paragraph + heading editing wired to ViewModel | ✅ | `updateParagraphById` / `updateSectionHeaderById` |
| **Edits preserve run formatting (sizes, bold, …)** | ❌ | `updateParagraph` collapses the paragraph to a **single plain `TextSpan`** and `toAnnotatedString()` drops all span styles — one keystroke strips all formatting (this is also parser-problem G5 in TODO1) |
| Edit text rendered with document metrics | ❌ | TextField uses `MaterialTheme.typography` — edit view never matches the canvas |
| Undo / redo | ❌ | Stubs in ViewModel |
| Canvas ↔ overlay pixel parity | 🟡 | Positions match; TextField's internal padding/baseline differ from `StaticLayout` draw |

## L. Infrastructure

| Element / feature | Status | Notes |
|---|---|---|
| Strict point/pixel/inch conversion standards | ✅ | `UnitConverter` (1in = 72pt; density-aware), tested round-trip |
| Measured-height cache per element | ✅ | `LayoutRegistry` |
| Typeface cache | ✅ | `AndroidTypefaceMapper` |
| Single source of truth for line-height math | ✅ | `TextFlowResolver` shared by Compose mapper and Canvas measurer, tested |
| Shared image scaling rules (widget ↔ paginator) | ✅ | `ImageScaleCalculator` |
| Pagination off the main thread | ✅ | `produceState` + `Dispatchers.Default` |
| Bundled/mapped document fonts (typeface files) | ❌ | Required for cross-device metric parity with Word/LibreOffice |
| Eager full-document pagination (memory/time for 500+ pages) | 🟡 | Whole document paginates before first frame; no streaming/incremental wiring (see B) |

## M. Tests & verification

| Element / feature | Status | Notes |
|---|---|---|
| Unit tests: page dimensions, section, spacing, indentation | ✅ | `PageDimensionsTest`, `SectionResolverTest`, `SpacingResolverTest`, `IndentationResolverTest` |
| Unit tests: flow rules, image scaling, line-height, fonts, justification | ✅ | `FlowControllerTest`, `ImageScaleCalculatorTest`, `TextFlowResolverTest`, `FontFamilyResolverTest`, `TextLayoutDecisionsTest`, `DocumentTextColorResolverTest`, `DocumentTextStyleMapperTest` |
| Table UI tests | ✅ | `TableUiMapperTest`, `TableColumnSizerTest` |
| **End-to-end pagination test (paginate a document, assert N pages + positions)** | ❌ | No `PrintPaginatorTest` exists |
| Page-count regression fixtures (DOCX/ODT golden files) | ❌ | — |
| Visual/golden screenshot comparison per page | ❌ | — |
| Calibration against Word/LibreOffice export (same doc, same page breaks) | ❌ | The defining WYSIWYG acceptance test |

---

## What remains (aggregated)

**R1 — R6: geometry & page count (the core ask):**

1. **R1** Wire document page setup into the renderer: project
   `ParsedDocument.sections` into `DocumentElement.Section` (or emit
   `BlockNode.SectionBreak` during parse) so width/height/margins/orientation
   from the file actually drive `PrintLayout` — today every document renders as
   A4 with 1-inch margins (A).
2. **R2** Expose the page count: `pageCount` (+ `currentPage`) in
   `EditorUiState`, "Page x of Y" indicator; wire `estimatePageCount` for a
   pre-layout value (B, J).
3. **R3** Inline page/column breaks: map `InlineNode.Break(PAGE/COLUMN)` to
   `DocumentElement.PageBreak` in `LegacyElementAdapter` (B).
4. **R4** Remove annotation pollution: exclude Metadata/Comment/Note/Bookmark/
   Field stubs from print flow (and later give notes a real footnote area) (I).
5. **R5** Draw list labels in print: include `listLabel` in `TextMeasurer`
   measurement + `SpannableTextBuilder` output so bullets/numbers appear and
   measurement stays correct (E).
6. **R6** First-line + hanging indents into `StaticLayout` (composition/
   prefix-spacer trick) (D).

**R7 — R12: typography & paragraph rhythm:**

7. **R7** Keep half-point font sizes (`TextSpan.fontSize` → Float) (C).
8. **R8** Heading sizes from document styles instead of the
   `12 + (4 − level)` heuristic (C).
9. **R9** Space-before actually offsetting the text (draw layout at
   `bounds.top + beforePt`) (D).
10. **R10** Apply caps/small-caps, skip hidden text (C).
11. **R11** Honor `pageBreakBefore`, keep-with-next, keep-lines (extend legacy
    `ParagraphStyle` + call `shouldKeepWithNext` in the paginator) (B, D).
12. **R12** Rich header/footer content (blocks instead of flattened text) and
    header/footer edge distances (H, A).

**R13 — R18: tables & images:**

13. **R13** Column widths from `tblGrid`/ODF column styles into the legacy
    model (F).
14. **R14** Cell merges (`gridSpan`/`vMerge`/`hMerge`) rendered as merged
    regions (F).
15. **R15** Document table styling: border colors/styles, shading, cell
    margins, row heights, per-cell run formats, cell alignment (F).
16. **R16** Image placement: inline-at-position vs centered, rotation, wrap
    around floating images (G).
17. **R17** Drawing extent (`widthEmu/heightEmu`) carried through
    `LegacyElementAdapter` → real shape sizes (G).
18. **R18** Text boxes rendered as positioned boxes (depends on parser F7/F8)
    (G).

**R19 — R24: page/section completeness & UI:**

19. **R19** Multi-section geometry switching live (depends on R1) + landscape
    verification for both formats (A).
20. **R20** Footer page numbers + field evaluation (PAGE/NUMPAGES) (H).
21. **R21** Zoom, actual-size mode, current-page tracking, "Page x of Y"
    pill (J).
22. **R22** Decide dark-mode paper policy (white paper + black ink = true
    WYSIWYG) (J).
23. **R23** Edit round-trip preserving run styles; edit text uses document
    metrics (K).
24. **R24** Bundled fonts for metric parity with Word/LibreOffice (C, L).

**R25 — R28: verification:**

25. **R25** `PrintPaginatorTest` end-to-end (assert page count + element
    positions for fixture documents) (M).
26. **R26** Golden page fixtures for DOCX + ODT (M).
27. **R27** Calibration pass against Word/LibreOffice exports — same document,
    same page breaks (M).
28. **R28** Wire `IncrementalPaginator`/`PaginationEngineFactory` for large
    documents (B, L).

---

## Recommended order of precedence

Work in **waves**. Each wave is shippable, ordered by "how wrong is the page
right now". Within a wave, work top-down.

### Wave 1 — Correct page box, correct page count (do first)
Everything else is meaningless while the page box is wrong.

| # | Item | Why first |
|---|---|---|
| 1 | **R1** Project parsed sections → `DocumentElement.Section` | Without this, width/height/margins/orientation from the file are ignored — no WYSIWYG possible |
| 2 | **R3** Inline page breaks → `PageBreak` elements | Word docs' explicit breaks silently ignored ⇒ wrong page count |
| 3 | **R5** List labels drawn in print | Visible content missing from every list |
| 4 | **R4** Exclude annotation stubs from print flow | Page 1 currently starts with "DOCX Metadata:…" rows not in the document |
| 5 | **R2** Expose `pageCount`/`currentPage` + "Page x of Y" | Your explicit requirement; falls out of the now-correct pagination |

### Wave 2 — Text metrics correctness (page-break parity)

| # | Item | Why second |
|---|---|---|
| 6 | **R7** Half-point font sizes | 10.5pt is common; rounding shifts every line wrap |
| 7 | **R6** First-line/hanging indents | Changes line wrap positions ⇒ changes page breaks |
| 8 | **R9** Space-before offset | Per-paragraph rhythm; cheap fix |
| 9 | **R8** Heading sizes from styles | Headings occupy wrong height today |
| 10 | **R11** pageBreakBefore / keep-with-next / keep-lines | Directly changes where pages break |
| 11 | **R10** caps/small-caps, hidden text | Width-affecting typography |

### Wave 3 — Tables & images (dominant height consumers after text)

| # | Item | Why third |
|---|---|---|
| 12 | **R13** Grid column widths | Table heights/line wraps wrong without real columns |
| 13 | **R15** Document table styling + cell formats/alignment/margins | Core table fidelity |
| 14 | **R14** Cell merges | Structure, not just skin |
| 15 | **R16 + R17** Image placement, rotation, drawing extent | Second-biggest layout contributor |

### Wave 4 — Sections, headers/footers, page furniture

| # | Item | Why fourth |
|---|---|---|
| 16 | **R19** Multi-section geometry + landscape both formats | Completes Wave-1 item R1 |
| 17 | **R12** Rich header/footer content + edge distances | Visible on every page |
| 18 | **R20** Footer "Page x of y" + field evaluation | Needs correct section/page numbers first |
| 19 | **R18** Text boxes (after parser F7/F8) | Depends on parser work |

### Wave 5 — Page view UX & edit fidelity

| # | Item | Why fifth |
|---|---|---|
| 20 | **R21** Zoom / actual size / current page tracking | UX on top of a correct engine |
| 21 | **R22** Dark-mode paper policy (white paper, black ink) | One-line decision + token change |
| 22 | **R23** Edit round-trip preserving run styles | Stops edits destroying formatting |
| 23 | **R24** Bundled fonts | Largest remaining metric-parity lever; heavier lift |

### Wave 6 — Prove it (never skip)

| # | Item |
|---|---|
| 24 | **R25** `PrintPaginatorTest` — assert page counts and positions for fixtures |
| 25 | **R26** Golden DOCX/ODT page fixtures |
| 26 | **R27** Calibration vs Word/LibreOffice export (same doc ⇒ same breaks) |
| 27 | **R28** Wire incremental pagination for large documents |

### Rules of engagement for every item

1. **Fixture first** — build a minimal `.docx`/`.odt` exercising the feature,
   assert the resulting `PageModel` list (count, bounds) in a JVM test where
   possible; use `PrintPaginator` directly with a fake `TextMeasurer` for pure
   flow logic.
2. **Measure == draw** — any change to `TextMeasurer` measurement must land in
   the same code path used by `PrintElementRenderer`, or page count and pixels
   drift apart.
3. **Mind the projection gap** — much of the needed data is already parsed into
   `ParsedDocument`/rich styles but dropped by `LegacyElementAdapter`. Prefer
   extending the adapter/legacy model over re-parsing.
4. **Parser blockers** — items marked "(parser)" in this file are tracked as
   F1–F28 in `parser_progress.md`; fix them there first.
5. **Tick the row** here and bump the counts in *Overall progress* when an
   item moves to ✅.
6. **Out of scope by agreement:** superscript/subscript shaping refinements
   (already basic-rendered), sub/superscript depth, complex nested-table
   shaping.
