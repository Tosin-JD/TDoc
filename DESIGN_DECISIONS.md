# TDoc Design Decisions

Recorded decisions for the TDoc parser roadmap (TODO2a–TODO2i). See `TODO2.md`.

## Phase 4 (TODO2e) — Parser Extensibility

- **Skip `ParserResult` wrapper (MVP).** `Result<List<DocumentElement>>` is sufficient.
  Warnings can be logged via `Log.w`; file size is already captured by the repository.
- **Skip `ParserError` sealed class (MVP).** Use `IllegalArgumentException` for unsupported
  formats, `IOException` for read failures, generic `Exception` for parse errors.
- All POI imports are confined to `data/parser/docx/`; the repository and UI never touch
  POI or ODFDOM. `OdfTextDocument` no longer appears anywhere in the app.

## Phase 5 (TODO2f) — Model Refinements

- **`TextSpan.hyperlink` — skip for MVP, defer to Phase 2.** Hyperlinks stay on
  `Paragraph.hyperlink` (single hyperlink per paragraph covers the common case).
- **`ParagraphStyle` — no change needed.**
- **`TableMetadata.preferredWidth` / `tableLayout` — skip for MVP.** Width/layout are
  rendering concerns.
- **`HeaderFooterContent.referenceId` / `linkedToSection` — skip for MVP.**
- **`DocumentElement.Hyperlink` / `DocumentElement.List` — skip for MVP.** Paragraphs
  carrying `listLabel`, `listInfo`, and `hyperlink` fields suffice.
- **`DrawingInfo`, `MetadataInfo` — no change needed.**

## ODT Writer Design (this session)

- **ODFDOM Simple API not used.** `odfdom-java:0.12.0` does not ship the Simple API
  (`org.odftoolkit.simple`). Parsing stays DOM-based (java.xml); saving uses a
  hand-written deterministic ODT writer (`OdtWriter`) producing `mimetype` (stored,
  first entry) + `META-INF/manifest.xml` + `content.xml` + `Pictures/`, with automatic
  paragraph/text styles deduplicated by property fingerprint.
- Heading paragraphs round-trip as `text:h` with `text:outline-level`, matching the
  ODT authoring convention and letting the parser classify them as headings again.

## Phase 7 testing decisions

- **Test fixtures are fully programmatic.** DOCX uses POI (`XWPFDocument`) plus
  in-memory ZIP builders, ODT uses `OdtTestFixtures`, TXT uses inline strings. No binary
  resource files under `src/test/resources/`; TODO2h's textual fixture files were
  therefore skipped as redundant with the established convention.
- **POI hex colors are `byte[]`.** `CTShd.fill` / `CTBorder.color` (ST_HexColor) are
  exposed as `byte[]`; `DocxTableParser` decodes them back to `RRGGBB` strings (raw
  `toString()` yielded `[B@...` garbage). Unit tests assert the same decode so a
  regression is caught.
- **`block.type.headingLevel`** (used in `EditorParagraph.kt`/`EditorScreen.kt`) is the
  `com.tosin.docprocessor.model.headingLevel` extension — no member property added.

## MVP release decisions

- **Room schema stays at v1 — no migration test yet.** The recents table shape is
  stable, the schema is exported to `app/schemas` (`room.schemaLocation`), and the DoD
  for TODO1j permits documenting this decision instead of shipping a migration test.
  The first schema change adds a migration test plus an exported v2 JSON.
- **Release minification enabled (R8).** `isMinifyEnabled`/`isShrinkResources` on, with
  `proguard-rules.pro` keeping `org.apache.poi.**`, `org.openxmlformats.**`,
  `org.apache.xmlbeans.**`, `org.odftoolkit.**`, and `DocumentElement` subtypes whole
  (reflection + exhaustive `when` dispatch), plus `-dontwarn` for the optional
  POI/XMLBeans references that are not on the Android classpath.
- **Adaptive launcher icon** is a brand teal (`#00696E`) rounded sheet with amber pen;
  `monochrome` layer reuses the foreground vector.