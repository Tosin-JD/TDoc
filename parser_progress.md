# Parser Progress — DOCX & ODT element coverage

Purpose: a single checklist of every element/feature the docx processor needs to
parse, with the current status marked, what remains, and the recommended order
to approach the remaining work.

Status derived from the actual implementation in
`app/src/main/java/com/tosin/docprocessor/data/parser/` (rich parser producing
`ParsedDocument` / `BlockNode` / `InlineNode`).

## Legend

| Mark | Meaning |
|------|---------|
| ✅ | Parsed into the rich model and reachable from `ParsedDocument` |
| 🟡 | Partially parsed — the note says what is missing |
| ❌ | Not parsed — dropped, or captured only as an `Opaque` placeholder |
| — | Not applicable to this format |

Legend for tables: **DOCX** = `data/parser/docx/`, **ODT** = `data/parser/odt/`.

---

## Overall progress

**Combined: 219 ✅ / 18 🟡 / 44 ❌ out of 281 applicable entries (~78 % complete).**

| Format | ✅ Done | 🟡 Partial | ❌ Remaining | Applicable total | Progress |
|---|---|---|---|---|---|
| DOCX | 119 | 7 | 19 | 145 | **82 %** (≈ 84 % counting partials) |
| ODT | 100 | 11 | 25 | 136 | **74 %** (≈ 78 % counting partials) |

Remaining work items are consolidated as **F7–F28** below (Wave 1, **F1–F6**,
is complete — see `TODO2a.md`), then ordered by
recommended precedence in *Recommended order of precedence*.

---

## A. Package & part discovery

| Element / feature | DOCX | ODT | Notes |
|---|---|---|---|
| ZIP entry reading with path-traversal, size and XXE safety checks | ✅ | ✅ | `OfficePackageSafetyTest` covers traversal, oversize, XXE |
| Content-type / manifest recognition (`[Content_Types].xml`, `mimetype`, `META-INF/manifest.xml`) | ✅ | ✅ | |
| Relationship resolution (OPC `.rels` ↔ ODF manifest + `xlink:href`) | ✅ | ✅ | External targets kept external (`DocxPackageTest`) |
| Unrecognised package parts preserved as opaque parts (round-trip safe) | ✅ | ✅ | `OpaquePackagePart` + package inventory report |
| Encrypted package detection with a clear error | 🟡 | ✅ | ODT explicitly rejects encrypted `content.xml`; DOCX only fails indirectly ("no main document part") |

## B. Body block elements

| Element / feature | DOCX | ODT | Notes |
|---|---|---|---|
| Paragraph (`w:p` / `text:p`) | ✅ | ✅ | |
| Heading (`w:p` with Heading style / `outlineLvl`; `text:h`) | ✅ | ✅ | Level from style name, `w:outlineLvl`, or `text:outline-level` |
| Table (`w:tbl` / `table:table`) | ✅ | ✅ | |
| List block creation | ✅ | ✅ | DOCX: consecutive `numPr` paragraphs grouped into `ListBlock` |
| Nested list hierarchy by level | ✅ | ✅ | DOCX: `ilvl` → nested `ListBlock` via `ListBlockGrouper` (TODO2a F3); ODT recurses by depth |
| Block-level content control (`w:sdt`) | ✅ | — | Content unwrapped into blocks; empty `sdtContent` preserved |
| Block-level tracked insert/delete (`w:ins`/`w:del`) | ✅ | ❌ | ODT `text:tracked-changes` → Opaque |
| Block-level tracked move (`w:moveFrom`/`w:moveTo`) | ❌ | ❌ | Inline moves are parsed (DOCX); block-level falls to Opaque |
| Section container (`text:section`) | — | ✅ | Mapped to `BlockNode.TextBox` |
| Text box content (`w:txbxContent` / inline `draw:text-box`) | ❌ | 🟡 | DOCX: shapes with text are captured Opaque. ODT: **block** frames with `draw:text-box` ✅, **inline** frames with text → Opaque |
| Soft page break as block (`text:soft-page-break`) | — | ✅ | |
| Index / TOC block (`text:table-of-content`, `alphabetical-index`, …) | ❌ | 🟡 | DOCX TOC only appears as a `Field`. ODT: `kind` parsed, `title`/`entries` empty |
| `w:altChunk` captured as Opaque | 🟡 | — | Preserved, not merged |
| Unknown block → `Opaque` fallback with diagnostics | ✅ | ✅ | Never silently dropped |

## C. Inline elements

| Element / feature | DOCX | ODT | Notes |
|---|---|---|---|
| Text runs (`w:t`, `w:delText`; text/CDATA nodes) | ✅ | ✅ | Source order preserved, runs never flattened |
| Tab (`w:tab` / `text:tab`) | ✅ | ✅ | |
| Line / page / column breaks (`w:br type`, `w:cr` / `text:line-break`) | ✅ | ✅ | |
| Explicit spaces (`text:s` with count) | — | ✅ | |
| Non-breaking & soft hyphens (`w:noBreakHyphen`, `w:softHyphen`) | ✅ | — | |
| Symbol run (`w:sym`) | ✅ | — | |
| Cached/soft page-break marker inline (`w:lastRenderedPageBreak` / `text:soft-page-break`) | ✅ | ✅ | |
| Hyperlink — external target, internal anchor, tooltip, docLocation / `text:a` | ✅ | ✅ | |
| Bookmark start/end boundaries | ✅ | ✅ | |
| Comment range anchors (`w:commentRange*` / `office:annotation` + `annotation-end`) | ✅ | ✅ | |
| Footnote/endnote reference inline | ✅ | ✅ | Body collected separately (see J) |
| Comment reference inline | ✅ | ✅ | |
| Complex field (`w:fldChar` begin/separate/end + `w:instrText`) | ✅ | — | Instruction + result nodes captured as `InlineNode.Field` |
| Simple field (`w:fldSimple`) | ✅ | — | |
| ODF text fields (`text:page-number`, `page-count`, `sheet-name`, `file-name`, `ref`, `sequence`, …) | — | ✅ | Mapped to `InlineNode.Field` with cached display text as `result` (TODO2a F5) |
| Inline tracked insert/delete/move (`w:ins`/`w:del`/`w:moveFrom`/`w:moveTo`) | ✅ | ❌ | ODT `text:change`, `change-start/end` → Opaque |
| Format-change revisions (`w:rPrChange`, `w:pPrChange`, ODF format changes) | ❌ | ❌ | `RevisionKind.FORMAT_CHANGE` exists but is never produced |
| Inline content control (inline `w:sdt` with tag/alias/dataBinding) | ✅ | — | |
| `w:smartTag` / `w:customXml` unwrapped, children parsed | ✅ | — | |
| Proof errors (`w:proofErr`) correctly skipped | ✅ | — | |
| Ruby (`w:ruby` base + rt) | ✅ | ❌ | ODT `text:ruby` children get concatenated by the fallback walk |
| Inline image/drawing with extent, rotation, alt text, title | ✅ | ✅ | `InlineNode.Drawing` |
| Frame position & wrap (x/y, wrap style, z-order) | 🟡 | 🟡 | Only size + inline/anchored flag + DOCX rotation; `positionX/Y`, `wrapStyle`, `zOrder` never set |
| Legacy VML picture (`w:pict` > `vml:shape` > `imagedata`) | ✅ | — | |
| Shapes without image payload (VML shapes, `draw:shape`) | ❌ | ❌ | Preserved Opaque |
| Charts (`c:chart` / `draw:chart`) | ❌ | ❌ | Preserved Opaque ("chart not rendered") |
| SmartArt / diagrams (`a:diagram`) | ❌ | — | Preserved Opaque |
| Embedded OLE objects (`w:object`, `draw:object`/`draw:ole`) | ❌ | ❌ | Preserved Opaque |
| Numbered text (`text:number`) | — | ✅ | |
| Note citation skipped in flow (`text:note-citation`) | — | ✅ | |
| Unknown inline → Opaque / child walk fallback | ✅ | ✅ | |

## D. Text (character) properties

| Property | DOCX (`w:rPr`) | ODT (`style:text-properties`) | Notes |
|---|---|---|---|
| Font family (ascii/hAnsi/eastAsia/complex-script / `fo:font-family`) | 🟡 | ✅ | DOCX: names captured but **theme fonts** (`asciiTheme` → `theme1.xml`) are not resolved |
| Font size | ✅ | ✅ | |
| Bold / italic | ✅ | ✅ | `onOff` tri-state; ODF weight/style values |
| Underline kind + color | ✅ | 🟡 | ODF: style mapped, `text-underline-color` not read |
| Strikethrough (single/double) | ✅ | ✅ | |
| Superscript / subscript / baseline position | ✅ | ✅ | `w:vertAlign`, `fo:position`/`style:text-position` |
| Color + theme color | 🟡 | ✅ | DOCX stores `themeColor` name only; no theme palette resolution |
| Highlight | ✅ | ✅ | |
| Run shading (`w:shd` / `fo:background-color`) | ✅ | ✅ | |
| All-caps / small-caps | ✅ | 🟡 | ODF caps via `fo:text-transform`; no small-caps equivalent |
| Letter spacing | ✅ | ✅ | |
| Kerning | ✅ | — | |
| Language | ✅ | ✅ | |
| Right-to-left / writing direction | ✅ | ❌ | ODF `fo:direction` / `style:writing-mode` not read |
| Hidden text (`w:vanish`/`webHidden`) | ✅ | ❌ | ODF visibility attributes not read |
| Character style resolution (`w:rStyle` / `text:span` style-name) | ✅ | ✅ | Chains resolved, cached |

## E. Paragraph properties

| Property | DOCX (`w:pPr`) | ODT (`style:paragraph-properties`) | Notes |
|---|---|---|---|
| Style reference | ✅ | ✅ | |
| Alignment | ✅ | ✅ | incl. justify/distribute |
| Indents start/end, first-line, hanging | ✅ | ✅ | ODF `text-indent` sign split into first-line vs hanging |
| Spacing before/after | ✅ | ✅ | |
| Line spacing + rule (auto 240ths / exact twips / %) | ✅ | ✅ | |
| keep-with-next | ✅ | ✅ | |
| keep-lines / keep-together | ✅ | ❌ | ODF `fo:keep-together` not read |
| Page-break-before | ✅ | ✅ | ODF `fo:break-before="page"` |
| Widow/orphan control | ✅ | — | |
| Outline level | ✅ | ✅ | |
| Tab stops (`w:tabs`) | ✅ | ❌ | ODF `style:tab-strokes` not read |
| Paragraph borders (`w:pBdr`) | ✅ | ✅ | ODF single `fo:border` → one border |
| Paragraph shading | ✅ | ✅ | |
| Paragraph-mark / default run props (`pPr/rPr`) | ✅ | ✅ | Feeds `defaultRunStyle` |

## F. Lists

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Numbering definition parsed (`word/numbering.xml` / `text:list-style`) | ✅ | ✅ | |
| Level format, level text, start value, indent, bullet font/char | ✅ | ✅ | |
| Level overrides (`w:lvlOverride` / `w:startOverride`) | ✅ | — | |
| Style → numbering links (`styleLinks`) | ✅ | — | |
| Bullet marker text | ✅ | ✅ | |
| Ordered marker with `%1` substitution, roman/letter formats | ✅ | ✅ | Shared `ListMarkerFormatter` (decimal/zero/letter/roman/bullet); ODF prefix/suffix templates substituted (TODO2a F1) |
| Sequential per-item renumbering (item 1, 2, 3…) | ✅ | ✅ | `ListNumberingState` per numId / list style; DOCX `startOverride` and ODF `start-value` honoured (TODO2a F2) |
| Nested list structure preserved (DOCX `ilvl`) | ✅ | ✅ | See B |
| ODF list `text:start` / `text:restart` / continue overrides | — | ❌ | |
| List-level indents carried into `ListStyle.levels` | ✅ | ✅ | |

## G. Tables

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Row / cell parsing, nested block content in cells | ✅ | ✅ | Nested tables work in both |
| Grid columns (`w:tblGrid` / `table:table-column` widths) | ✅ | ✅ | ODT: column styles + `number-columns-repeated` expanded into `gridColumns` (TODO2a F4) |
| Header rows (`w:tblHeader` / `table:table-header-rows`) | ✅ | ✅ | |
| Row height (`w:trHeight` / `style:row-height`) | ✅ | ✅ | ODT via `style:row-properties` (TODO2a F6) |
| Column span (`w:gridSpan` / `number-columns-spanned`) | ✅ | ✅ | |
| Row span / vertical merge (`w:vMerge` / covered cells + `number-rows-spanned`) | ✅ | 🟡 | ODT restart-only; no `CONTINUE` propagation across repeated rows |
| Horizontal merge (`w:hMerge`) | ❌ | — | |
| Repeated columns (`number-columns-repeated`) | — | ✅ | |
| Repeated rows (`number-rows-repeated`) | — | ✅ | Expanded with cap + diagnostic (TODO2a F6) |
| Cell width (`w:tcW` / cell style width) | ✅ | ❌ | |
| Table width, alignment, fixed/auto layout | ✅ | 🟡 | ODT: width + align, no layout concept (N/A) |
| Table borders (per-side) | ✅ | ✅ | ODT: single `fo:border` applied to all sides |
| Table shading | ✅ | ✅ | |
| Cell borders (per-side) | ✅ | ✅ | |
| Cell margins / padding (`w:tcMar`, `w:tblCellMar` / `fo:padding`) | ❌ | 🟡 | ODT: only detects that padding exists (currently reuses border values — suspicious); DOCX not read at all |
| Cell vertical align | ✅ | ✅ | |
| Cell text direction | ✅ | ❌ | ODF direction attribute not read |
| Table caption / description | ✅ | ❌ | DOCX `tblCaption`/`tblDescription`; ODT `table:name` etc. not read |
| Table style resolved from styles (borders, banding `firstRow`/`bandRow`) | 🟡 | ✅ | DOCX `parseTableStyle` only reads `w:shd` |
| Row properties (`w:cantSplit`, …) | ❌ | ❌ | Header flag is parsed; splitting not |

## H. Sections & page setup

| Feature | DOCX (`w:sectPr`) | ODT (master pages / page layouts) | Notes |
|---|---|---|---|
| Section collection (paragraph-level + final `sectPr`) | ✅ | ✅ | ODT: master pages listed from `styles.xml` |
| Page size + orientation | ✅ | ✅ | |
| Margins incl. header/footer/gutter | ✅ | ✅ | |
| Columns: count + spacing | ✅ | ✅ | |
| Columns: widths, `equalWidth`, `separate` | ❌ | ❌ | |
| Page-number start (`w:pgNumType`) | ✅ | ❌ | |
| Different first page (`w:titlePg` / `header-first`) | ✅ | ✅ | |
| Even/odd headers (`w:evenAndOddHeaders` / `header-left`) | ✅ | ✅ | |
| Section break type (`w:type` next/continuous) | ✅ | 🟡 | ODT type hardcoded to `nextPage` |
| Master-page ↔ content binding (which section uses which master page) | — | ❌ | Sections are listed but not bound to content, so per-page geometry can't be assigned |
| Page borders (`w:pgBorders`) | ❌ | ❌ | |
| Line numbering (`w:lnNumType`) | ❌ | — | |
| Footnote/endnote placement props in `sectPr` | ❌ | — | |
| Section vertical alignment (`w:vAlign`) | ❌ | ❌ | |
| Page background color/image | ❌ | ❌ | |

## I. Headers & footers

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Header/footer parts parsed as full block stories | ✅ | ✅ | Full `BlockNode` lists (paragraphs, tables, images) |
| Variants: default / first / even | ✅ | ✅ | |
| Dedup of shared header/footer references | ✅ | ✅ | |
| Field evaluation inside stories (PAGE, NUMPAGES, STYLEREF, `text:page-number`) | 🟡 | 🟡 | Both formats capture `Field` nodes with cached display text (ODT via TODO2a F5); live evaluation is F19 |

## J. Notes & comments

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Footnote reference + body | ✅ | ✅ | |
| Endnote reference + body | ✅ | ✅ | |
| Separator/continuation entries filtered (id ≤ 0) | ✅ | — | |
| Note body parsed as blocks | ✅ | ✅ | |
| Comment definition (author, initials, date, body blocks) | ✅ | ✅ | ODT `office:annotation` (no initials fallback in meta) |
| Comment anchors in document flow | ✅ | ✅ | |

## K. Metadata & settings

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Core properties (title, subject, creator, dates, revision, keywords, …) | ✅ | ✅ | |
| Extended properties (app, company, template, word/page stats) | ✅ | ✅ | ODT via `meta:document-statistic` |
| Custom / user-defined properties | ✅ | ✅ | |
| Settings: protection, track-changes, even/odd headers, view/zoom | ✅ | ✅ | ODT via `config-item` map |
| Default tab stop | ✅ | 🟡 | ODT `parseSettings` never reads `TabStop` config item |

## L. Styles

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Style catalogue built and exposed on `ParsedDocument` | ✅ | ✅ |
| Inheritance chain (`w:basedOn` / `style:parent-style-name`), cycle-safe | ✅ | ✅ |
| Paragraph / character / table style families separated | ✅ | ✅ |
| List styles in catalogue | ✅ | ✅ |
| Document defaults (`w:docDefaults` / `style:default-style`) | ✅ | ✅ |
| Automatic styles (content.xml) override common styles | — | ✅ | `OdtStyleParserTest` |
| Theme font resolution (`word/theme/theme1.xml`) | ❌ | — | Needed to render `asciiTheme` fonts correctly |

## M. Images & resources

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Embedded image bytes collected (`ResourceCollector`) | ✅ | ✅ | |
| External image targets flagged, not inlined | ✅ | ✅ | |
| Intrinsic size carried (`wp:extent` / `svg:width`/`height`) | ✅ | ✅ | |
| Alt text + title | ✅ | ✅ | `wp:docPr descr/title`, `svg:title`/`desc` |
| Rotation | ✅ | ❌ | ODF `draw:transform` not parsed |
| Figure caption extraction (`figcaption`, `text:caption`) | ❌ | ❌ | `Figure.caption` exists but is never populated |

## N. Diagnostics & preservation (infrastructure)

| Feature | DOCX | ODT | Notes |
|---|---|---|---|
| Unknown construct → `OpaqueNode` with captured XML fragment | ✅ | ✅ | Testable via `preserves unknown block constructs…` |
| Diagnostics collector (preserve/info, compatibility level) | ✅ | ✅ | |
| Package inventory report | ✅ | ✅ | |

---

## What remains (aggregated)

**Structural fidelity:**

1. **F7** DOCX text boxes (`w:txbxContent`, VML `v:textbox`) → Opaque instead of `BlockNode.TextBox` (B).
2. **F8** ODT **inline** frames containing text boxes → Opaque (only block frames work) (B).
3. **F9** DOCX cell margins (`w:tcMar`, `w:tblCellMar`) and `w:hMerge` not parsed (G).
4. **F10** ODT cell margins/`fo:padding` handling is wrong (reuses border values); cell width not read (G).
5. **F11** ODT tracked changes (`text:tracked-changes`, `text:change*`) → Opaque instead of `Revision` nodes (B, C).
6. **F12** DOCX block-level `w:moveFrom`/`w:moveTo`, and format-change revisions (`w:rPrChange`/`w:pPrChange`) in both formats (B, C).
7. **F13** DOCX table style from `styles.xml` only reads shading — borders, banding rows missing (G).
8. **F14** Frame position/wrap/z-order never populated (`FrameStyle.positionX/Y`, `wrapStyle`, `zOrder`) (C).
9. **F15** ODT table caption/description, row-span `CONTINUE` propagation, cell text direction (G).

**Page/section completeness:**

10. **F16** ODT master-page ↔ content binding (per-section geometry) (H).
11. **F17** DOCX `sectPr` extras: `w:pgBorders`, `w:lnNumType`, column widths, note placement, `w:vAlign` (H).
12. **F18** Theme font resolution (`theme1.xml`) for `asciiTheme` fonts (D, L).
13. **F19** Header/footer field evaluation (PAGE/NUMPAGES/…) (I).
14. **F20** ODT `keep-together`, tab strokes, writing direction; default tab stop (E, K).
15. **F21** Page background color/image (H).

**Advanced / rare (lowest urgency):**

16. **F22** TOC/index entry + title extraction — DOCX TOC is only a `Field`; ODT `IndexBlock.entries` empty (B).
17. **F23** Charts: render or at least a structured (non-Opaque) node (C).
18. **F24** SmartArt / diagrams (C).
19. **F25** Embedded OLE objects (`w:object`, `draw:ole`) (C).
20. **F26** Figure/caption extraction into `Figure.caption` (M).
21. **F27** ODT `text:ruby`, definition lists, `text:start`/`restart` list overrides (C, F).
22. **F28** Graceful encrypted-DOCX detection; glossary document; `w:altChunk` merge (A, B).

---

## Recommended order of precedence

Work in **waves**. Each wave is shippable on its own and is ordered by
"how badly does this corrupt a real document today". Within a wave, do the
items top-down.

### Wave 1 — Correctness of visible content ✅ **done** (implemented in `TODO2a.md`)

| # | Item | Why first |
|---|---|---|
| ✅ 1 | **F1 + F2** ODT `%1` substitution + sequential numbering in both formats | Ordered lists currently display garbage / all "1." |
| ✅ 2 | **F3** DOCX nested list hierarchy (`ilvl` → nested `ListBlock`) | Every multi-level Word list renders flat |
| ✅ 3 | **F4 + F6** ODT grid columns, repeated rows, row heights | Table layout has no column geometry at all |
| ✅ 4 | **F5** ODT text fields → `InlineNode.Field` | Page numbers/refs silently vanish, also fixes header/footer content |

### Wave 2 — Structural fidelity
Elements that appear often in real documents and currently degrade to Opaque.

| # | Item | Why second |
|---|---|---|
| 5 | **F7 + F8** Text boxes (DOCX `txbxContent`, ODT inline frames) | Common in real docs; `BlockNode.TextBox` already exists |
| 6 | **F11 + F12** Tracked changes → `Revision` nodes (both formats) | `RevisionKind` already modelled; ODF currently Opaque |
| 7 | **F9 + F10 + F13 + F15** Table completion: cell margins, `hMerge`, table style borders/banding, ODT caption + row-span propagation | Table rendering is a core docx-processor feature |
| 8 | **F14** Frame position/wrap/z-order | Needed before layout can honour floating content |

### Wave 3 — Page & section completeness

| # | Item | Why third |
|---|---|---|
| 9 | **F16** ODT master-page ↔ content binding | Unblocks correct multi-page ODT rendering (matches the `TODO1.md` multi-section follow-up) |
| 10 | **F17** DOCX `sectPr` extras (page borders, line numbering, column widths, note placement) | Pagination metadata |
| 11 | **F19** Header/footer field evaluation | Page numbers in margins |
| 12 | **F18** Theme font resolution | Font fidelity once everything else is stable |
| 13 | **F20 + F21** ODT keep-together/tabs/direction, page background | Polish of pagination behaviour |

### Wave 4 — Advanced / rare constructs (last)

| # | Item |
|---|---|
| 14 | **F22** TOC/index entry extraction (both formats) |
| 15 | **F26** Caption extraction |
| 16 | **F23 + F24 + F25** Charts, SmartArt, OLE objects |
| 17 | **F27 + F28** ODF ruby/definition lists/list restarts; encrypted DOCX, glossary, altChunk merge |

### Rules of engagement for every item

1. **Add a fixture first** — a minimal `.docx`/`.odt` (or inline XML in the
   existing test style) exercising the element, asserted against
   `DocxRichParserTest` / `OdtRichParserTest`.
2. **Map to the existing model** before inventing new types — most gaps
   (`TextBox`, `Revision`, `Field`, `Figure.caption`, `FrameStyle.wrapStyle`,
   `CellStyle.margins`, `ListStyle.levels`) already have slots waiting.
3. **Never regress the Opaque guarantee** — if an element stays unparsed it
   must still be captured as `Opaque` with a diagnostic, never dropped.
4. **Tick the row in this file** and bump the counts in *Overall progress*
   when an item moves to ✅.
