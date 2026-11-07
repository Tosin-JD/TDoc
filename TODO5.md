# TODO5 — Table Borders & Responsive Table Rendering

Research and implementation plan for making tables render with their
document-authored borders in **mobile view** and **print (WYSIWYG) view**,
following the existing architecture. No code has been changed yet.

---

## 1. Requirements (from the request)

1. Tables must show their borders — currently they are invisible in mobile
   view **and** in print view.
2. Border colour, edge style, width and other basic properties must match
   what the source document (docx / odt) defines.
3. Mobile view: tables should behave like Bootstrap's responsive tables —
   readable on small screens without losing the document's look.
4. Print view: tables must render as close as possible to the WYSIWYG
   result in Word / LibreOffice (Docx / Odt), so the user can understand the
   document as authored.

---

## 2. Current architecture (as it exists today)

The table pipeline already has a well-defined three-layer structure. The
problem is **not** that rendering is missing — borders are *supposed* to
flow through it. The gaps are in (a) style resolution coverage at parse
time, (b) a few renderer/paginator details, and (c) one pagination bug.

```
word/document.xml ─┐
word/styles.xml   ─┼─► DocxTableMapper / OdfTableMapper
styles.xml/content.xml►        │  (per-cell + table + style borders)
                   ▼
        BlockNode.Table (style: TableStyle, cells: CellStyle.borders/margins/shading)
                   │
                   ▼  LegacyElementAdapter.table()          [LegacyElementAdapter.kt:417]
        DocumentElement.Table { richRows, gridColumnWidthsPt,
                                 rowHeightsPt, metadata, presentation }
                   │
        ┌──────────┴───────────────────────────┐
        ▼ (mobile)                              ▼ (print)
 TableUiMapper.mapPresentation          PrintPaginator.buildTableLayout
        │  (TablePresentation →               │  applyResolvedPresentation overlays
        │   TableUiModel)                     │  presentation onto TableCellLayout
        ▼                                      ▼
 DocumentTable / TableWidget           PageView → PrintElementRenderer.renderTable
 (Compose Layout + drawBehind)         (Canvas drawRect/drawLine per cell)
```

### Key files

| Layer | File | Role |
|---|---|---|
| Domain model | `domain/document/model/style/TableStyle.kt` | `TableStyle`, `CellStyle`, `CellBorders`, `Border`, `TableRegion`, `VerticalMerge` |
| Presentation resolver | `domain/rendering/table/TablePresentation.kt` | `TablePresentationResolver` — full precedence chain `cell > conditional region > table > none`, shared-edge ownership (`paintBorders`), merge/grid normalization |
| Adapter | `data/parser/common/LegacyElementAdapter.kt:417-472` | Builds `DocumentElement.Table`, always sets `presentation` |
| DOCX parse | `data/parser/docx/mapper/DocxTableMapper.kt`, `resolve/DocxBorderProperties.kt`, `resolve/DocxStyleResolver.kt` | `w:tbl`, `w:tr`, `w:tc`, `w:tblBorders`, `w:insideH/V`, `w:tcBorders`, `w:tblStylePr` regions |
| ODT parse | `data/parser/odt/mapper/OdfTableMapper.kt`, `resolve/OdfProperties.kt` (`OdfTableProperties`), `resolve/OdfStyleResolver.kt` | `table:table`, cell/row/column styles, `fo:border*` |
| Mobile UI | `ui/components/TableWidget.kt`, `ui/components/table/TableUiMapper.kt`, `TableColumnSizer.kt`, `TableLayoutConfig.kt`, `model/TableUiModel.kt` | Compose table: sizing, scroll, cell bg/borders/text |
| Mobile host | `ui/editor/layouts/MobileLayout.kt`, `MobileContentPolicy.kt`, `ui/editor/renderer/DocumentElementRenderer.kt` | LazyColumn body list; `DocumentElement.Table → TableWidget` |
| Print | `domain/pagination/PrintPaginator.kt` (`buildTableLayout`, `applyResolvedPresentation`, `splitTableElement`), `ui/editor/layouts/print/PageView.kt`, `core/rendering/PrintElementRenderer.kt` | Page-faithful Canvas rendering |

### Design rules already established (must be preserved)

- **No synthetic styling.** Absent borders/shading stay transparent; the app
  theme (`DocumentColorTokens.tableBorder` etc.) must *not* be substituted
  for document-authored values. Both renderers document this explicitly.
- `Border.style` values `nil`/`none`/`zero` or `widthPoints <= 0` are
  explicit no-paint markers, distinct from "unspecified" (`null` side).
- Border precedence: `cell direct` > `conditional table-style region`
  (`w:tblStylePr`) > `table direct/style` (`w:tblBorders` outer vs
  `insideH`/`insideV` interior) > none.
- Shared-edge ownership in `TableCellPresentation.paintBorders`: a cell
  owns its **top** and **left** edges; bottom/right are painted by the
  neighbour or, on the table boundary, by the cell itself. `edgeTop` /
  `edgeLeft` fall back to the neighbour's resolved opposite border.

---

## 3. Root-cause analysis — why borders are invisible

The rendering code is essentially complete. Borders disappear because the
**resolved border data never reaches the renderers** for common real-world
documents. Ranked by likelihood:

### 3.1 ODT: style inheritance chains are not resolved for tables/cells (HIGH)

`OdfStyleResolver` resolves `parent-style-name` chains for text and
paragraph styles only:

- `textStyle()` / `paragraphStyle()` call `chain(name, pool)`
  (`OdfStyleResolver.kt:43-61`).
- `tableStyle()` (line 70-75) and `cellStyle()` (line 77-82) read
  `tablePool[name]` / `cellPool[name]` **directly, with no `chain()` call**.

LibreOffice routinely puts `fo:border*` on a *parent* automatic style
(e.g. a shared `Table1.A1`-style parent or a named cell style inherited by
`parent-style-name`). Those borders are silently dropped → fully borderless
tables in both mobile and print.

### 3.2 DOCX: table-style `basedOn` inheritance is not resolved (MEDIUM-HIGH)

`DocxStyleResolver.tableStyle()` (`DocxStyleResolver.kt:60-66`) returns
`raw.tableStyle` directly — `inheritanceChain()` is only applied to
paragraph and character styles. A custom `w:style w:type="table"` with
`w:basedOn w:val="TableGrid"` loses the entire inherited
`w:tblBorders`/`w:insideH`/`w:insideV`/`w:tblStylePr` region set.

### 3.3 ODT: border shorthand parsing is fragile (MEDIUM)

`OdfParagraphProperties.parseBorder()` (`OdfProperties.kt:162-171`):

- Assumes `parts.firstOrNull()` is the width. ODF allows the style keyword
  first (`solid 0.5pt #000000`) → `widthPoints = null` → renderer falls back
  to a default width, losing the authored width.
- Only accepts `#RRGGBB` colors (`parts.lastOrNull()?.takeIf { it.startsWith("#") }`).
  Named/X11 colors and `rgb()` forms are dropped → falls back to text colour.
- `style:relief`, `style:shadow`, and `table:border-model`
  (`collapsing` vs `separating`) on `style:table-properties` are ignored.
  `collapsing` is the Word-like shared-border model — the existing
  `paintBorders` ownership logic already matches it, so this is mostly a
  documentation/verification item.

### 3.4 Print: `splitTableElement` corrupts `presentation` row alignment (HIGH for long tables)

`PrintPaginator.splitTableElement()` (`PrintPaginator.kt:389-446`) slices
`rows`, `richRows` and `rowHeightsPt` for page continuation **but leaves
`element.presentation` untouched** — it still describes *all* rows.
`applyResolvedPresentation()` (`PrintPaginator.kt:942-960`) then indexes
`presentation.rows.getOrNull(rowIndex)` from 0 for the continuation
fragment, so every cell on the second page gets the wrong row's borders,
shading and margins (typically the header row's).

### 3.5 Print: border paint order can be overdrawn by cell shading (LOW-MEDIUM)

`PrintElementRenderer.renderTable()` draws, per cell, in one pass:
background → borders → text. A later cell's `drawRect` background covers
the outer half of a previous cell's border stroke (strokes are centred on
the edge). Mobile has the same issue in `TableWidget.drawCell` (background
+ borders in one `drawBehind`, placed cells paint in order). Fix = two
passes: all backgrounds first, then all borders (or inset border strokes
by half width).

### 3.6 Print: resolved presentation ignores table width/alignment (MEDIUM for WYSIWYG)

`TablePresentation.widthPoints` and `alignment` are resolved but never used:
`addElementToPage` always gives the table the full printable width, and
`buildTableLayout` scales grid widths to `effectiveWidth` regardless of
`w:tblLayout` (`fixed` vs `autofit`) or `w:tblW`. A narrow centred table in
Word prints full-width in TDoc. `TableCellPresentation.widthPoints` is also
dropped. Mobile likewise always fills or scrolls — acceptable for reading,
but print should honour authored geometry.

### 3.7 DOCX minor gaps

- `w:tblPrEx` (table property exceptions inside `w:tblPr`) not parsed.
- Border `w:themeColor`/`w:themeTint`/`w:themeShade` ignored → colour falls
  back to text colour (still visible; cosmetic).
- `w:space` (border-to-content spacing) parsed into `Border.space` but
  unused by both renderers.
- Border styles `wavy`, `dashDot`, `threeDEmboss` etc. only partially mapped
  in `drawBorderSide` (dash/dot/double handled; others draw solid).

### 3.8 What is NOT the problem

- `TablePresentationResolver` itself is correct and well-tested
  (`TablePresentationResolverTest`, `TableUiMapperTest`,
  `TableColumnSizerTest`, `PrintLayoutWysiwygTest`). Precedence, nil
  suppression, insideH/insideV mapping, merge normalization all work.
- `TableUiMapper.mapPresentation` correctly projects `paintBorders`,
  shading, margins and spans into `TableUiModel`.
- `TableWidget`/`DocumentTable` already draws per-side borders with
  colour/style/width (`drawCell`/`resolveUiBorder`, `TableWidget.kt:308-415`)
  and already implements horizontal scrolling for overflow
  (`horizontalScroll` + `TableColumnSizer`).
- `PrintElementRenderer.drawCellBorders` already draws resolved borders
  with dash/dot/double support (`PrintElementRenderer.kt:342-417`).

**Conclusion:** the fix is mostly *data plumbing* (inheritance chains,
shorthand parsing, split-table slicing) plus a small set of renderer
refinements — not a new rendering engine.

---

## 4. Mobile view — plan (Bootstrap-style responsive tables)

Bootstrap's model for tables on small screens is `.table-responsive`:
the table keeps its natural column widths inside a horizontally
scrollable wrapper; it never reflows cells into cards by default.
TDoc's `DocumentTable` already implements exactly this
(`BoxWithConstraints` → `TableColumnSizer.compute` → `horizontalScroll`).
The work is to make it *correct* and slightly smarter:

### 4.1 Ensure borders actually reach `DocumentTable`

Covered by §3 fixes (3.1–3.3). No widget change needed for borders
themselves — `drawCell` already honours `paintBorders`, colour, width,
dash/dot/double.

### 4.2 Responsive sizing refinements (keep Bootstrap semantics)

- `TableLayoutConfig.Default.minColumnWidthDp = 120f` is large for phones.
  Make the minimum **viewport-aware** inside `DocumentTable` (e.g.
  `minColumnWidthDp = when { maxWidth < 360.dp -> 88f; maxWidth < 600.dp -> 104f; else -> 120f }`)
  passed as a derived `TableLayoutConfig`, keeping `Default` unchanged for
  tests/other callers. This is a layout affordance, not styling — allowed
  by `TableLayoutConfig`'s contract.
- Keep `requiresScroll` → `horizontalScroll`; this *is* the Bootstrap
  `.table-responsive` equivalent. Do **not** introduce per-cell stacking;
  it would break merged cells and diverge from "as the document defines".
- Honour `presentation.widthPoints`/`alignment` in `TableUiMapper` only as
  a *maximum* width hint: a fixed-width table narrower than the viewport
  should be sized to `widthPoints` (converted pt→dp) and aligned per
  `alignment` (start/center/end) instead of always stretching — matching
  Word's `w:tblW`/`w:jc`. Tables with `widthPoints == null` (auto) keep
  current fill-or-scroll behaviour. Add fields to `TableUiModel`
  (`preferredWidthDp`, `alignment`) — additive, no breaking change.
- Cell text already uses authored font size via spans; no change.

### 4.3 Optional polish (document, decide before implementing)

- Cell vertical alignment already works (`VerticalCellAlign`).
- `Border.space` (pt) could be added to cell padding — currently ignored.
- `table.caption`/`description` exist in the model but aren't rendered;
  out of scope unless requested.

---

## 5. Print view — plan (WYSIWYG fidelity)

Goal: `PrintElementRenderer.renderTable` output ≈ Word/LibreOffice PDF.

### 5.1 Fix split-table presentation misalignment (required)

In `splitTableElement`, slice `presentation` alongside `rows`/`richRows`:

- Add a helper that returns `presentation.copy(rows = slicedRows)` using the
  same `rowsThatFit`/`headerRows` logic (repeat header row on the
  continuation, exactly like `splitRows` does for `richRows`).
- Keep `gridColumnWidthsPt`, `borders`, `insideBorders`, `cellMargins`,
  `shading`, `widthPoints`, `alignment` unchanged (they are table-level).
- Covered/merged cells spanning the split boundary are a known hard case;
  minimum viable fix = row-aligned slicing; document residual limitation.

### 5.2 Honour table geometry

- `TableRenderLayout`: add `widthPoints`/`alignment` (from
  `element.presentation`) or compute in `buildTableLayout`.
- In `buildTableLayout`: if `presentation.widthPoints` is set **and**
  `style.layout == "fixed"` (or width < printable width), use the authored
  width instead of always `effectiveWidth`; distribute per
  `gridColumnWidthsPt` proportions. Otherwise keep current scale-to-fit.
- In `addElementToPage`/`createLayoutResult` for `DocumentElement.Table`:
  compute `bounds` width = table width (pt→px via existing converters) and
  offset `left` per `alignment` (CENTER → centre in printable area,
  END → right-align). `PrintElementRenderer.renderTable` already draws
  within `bounds`, so only the bounds computation changes.
- Long tables wider than the printable area: scale down proportionally
  (Word does this for fixed-width tables) rather than clipping.

### 5.3 Paint order fix

Restructure `renderTable` into two passes over `rowLayouts`:

1. Pass A: table shading + every cell background `drawRect`.
2. Pass B: every cell's borders (`drawCellBorders`), then text.

This guarantees no cell background can erase a neighbour's border, matching
Word's "borders on top of shading". Apply the same two-pass idea in
`TableWidget`'s `TableGrid` (backgrounds layer, then borders layer, then
text) — the custom `Layout` already supports multiple placeables per cell;
add one more placeable per cell for the border layer, or draw borders in a
single `drawBehind` on the table container using the same column/row offset
math already computed there.

### 5.4 Use `paintBorders` (shared-edge ownership) in print too

`applyResolvedPresentation` currently copies `resolved.borders` (all four
sides). Every cell then paints all resolved sides — shared edges are drawn
twice at identical coordinates (harmless but wasteful, and it bypasses the
ownership model). Change it to carry `paintBorders` through
`TableCellLayout` (add a `paintBorders: CellBorders?` field, keep `borders`
for fallback) and have `drawCellBorders` prefer it. This also fixes the
edge case where two neighbours resolve a shared edge differently —
ownership makes the winner deterministic (top/left owner wins, per
`TablePresentationResolver`).

### 5.5 Border fidelity details

- `resolveBorder` already maps `nil/none/zero` → skip, `widthPoints<=0` →
  skip, authored colour via `DocumentTextColorResolver.parseColorString`,
  fallback = page text colour. Keep.
- Extend style mapping modestly: `wavy` → `PathEffect` approximation is
  not native; document as "draws solid" limitation OR add a simple wave
  path if cheap. `dashDot`/`dashSmallGap` already grouped with dashed.
- Honour `Border.space` as extra inset when stroking? In OOXML `w:space`
  is the gap between border and cell content — fold it into
  `resolveCellPaddingPx` (add `space` to the side's padding) — small change,
  real fidelity win.
- `insideBorders == null` + outer `borders` set: current resolver yields
  borderless interior — **spec-correct for OOXML** (Word's Table Grid
  style always sets insideH/insideV). Keep spec behaviour; if the user's
  actual file still shows no interior lines after §3 fixes, revisit with
  that file's XML.

---

## 6. Ordered implementation steps

1. **ODF inheritance** — `OdfStyleResolver.tableStyle()`/`cellStyle()`:
   resolve `parent-style-name` via `chain()` and merge
   `TableStyle`/`CellStyle` per-side (new `CellBorders.overlay`-style merge
   for `CellStyle`; `TableStyle` needs a merge helper or per-field
   resolution order: borders, insideBorders, cellMargins, shading,
   widthPoints, alignment). Add unit tests in `OdfListAndTableTest` /
   new `OdfTableStyleInheritanceTest`.
2. **DOCX table-style inheritance** — `DocxStyleResolver.tableStyle()`:
   walk `basedOn` chain over `tableStyles`, merge `tblPr`/`tblStylePr`
   results child-over-parent (direct formatting still wins via existing
   `overlay` in `DocxTableMapper`). Unit test in `DocxTableMappingTest`.
3. **ODF border shorthand** — rewrite `OdfParagraphProperties.parseBorder`
   to scan all tokens: find width token (`parseOdfLength` non-null or
   thin/medium/thick keywords → 0.75/1.5/2.5 pt-ish mapping per CSS/ODF
   conventions), style token, colour token (`#hex`, `rgb()`, named-colour
   small map). Keep `none|hidden` → explicit no-paint. Unit tests.
4. **Print split fix** — slice `presentation.rows` in
   `splitTableElement` (mirror `splitRows` incl. header repetition). Unit
   test in `PrintLayoutWysiwygTest` style.
5. **Print `paintBorders`** — add field to `TableCellLayout`, populate in
   `applyResolvedPresentation`, consume in `drawCellBorders`.
6. **Print geometry** — `widthPoints`/`alignment`/`layout=fixed` through
   `TableRenderLayout` → `buildTableLayout` → `addElementToPage` bounds.
7. **Print paint order** — two-pass backgrounds→borders→text in
   `renderTable`.
8. **Mobile polish** — viewport-aware `minColumnWidthDp`, optional
   `widthPoints`/`alignment` sizing in `TableUiModel` + `DocumentTable`,
   `Border.space` folded into padding.
9. **Verification** — unit tests per step; manual check with a real
   docx (Table Grid style) and odt (LibreOffice default table) in both
   MobileLayout and PrintLayout, light + dark.

---

## 7. Edge cases & risks

- **`w:insideH`/`w:insideV` absent**: interior stays borderless (spec).
  Confirm against the reporter's actual file before adding any fallback —
  a fallback would paint grids Word itself wouldn't show.
- **Border colour `auto`/theme colours**: currently → text colour. Dark mode
  uses `DocumentColorTokens.contentText` — correct behaviour; do not switch
  to `tableBorder` token (that would violate the no-synthetic-styling rule).
- **Collapsed vs separated border model (ODF)**: `paintBorders` ownership
  assumes collapsing; `table:border-model="separating"` would ideally draw
  both sides of shared edges — rare; document as limitation.
- **Nested tables**: `cellSpans` flattens nested `BlockNode.Table` to text —
  unchanged by this work.
- **Covered merge cells**: borders must come from the anchor only —
  already handled; don't regress in the paint-order refactor.
- **Performance**: `TablePresentationResolver.resolve` runs once per table
  at adapt time; print re-layout is debounced 300 ms. The paint-order
  change is O(cells) — negligible.

---

## 8. Test plan

- Unit: `OdfStyleResolver` inheritance merge; `parseBorder` shorthand
  variants; `DocxStyleResolver` table `basedOn`; `splitTableElement`
  presentation slicing; `TableUiMapper` width/alignment projection.
- Existing suites to keep green: `TablePresentationResolverTest`,
  `TableUiMapperTest`, `TableColumnSizerTest`, `PrintLayoutWysiwygTest`,
  `DocxTableMappingTest`, `OdfListAndTableTest`, `DocumentTableTest`
  (androidTest).
- Manual: border visibility in mobile + print for (a) docx "Table Grid",
  (b) docx direct `w:tcBorders`, (c) odt LibreOffice default table,
  (d) table split across two pages, (e) merged cells, (f) dark mode.
