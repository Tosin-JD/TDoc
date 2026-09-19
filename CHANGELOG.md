# Changelog

All notable changes to TDoc are tracked here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - MVP

### Added
- **Home screen**: recent-files list (ordered by last opened, capped at 20),
  "Open file" and "New document" via the system document picker, with loading,
  empty and error states.
- **Editor**: rich-text editing of DOCX, ODT and TXT documents.
  - Bold / italic / underline / strikethrough with surge selection semantics.
  - Heading levels, alignment and bullet lists from the formatting toolbar.
  - Undo / redo (toolbar buttons and Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y).
  - Paragraph insertion on Enter, tables and images kept their positions,
    page-break-aware "print" and "mobile" render modes.
- **Open/save flows**: open a document via `VIEW` / `EDIT` / `SEND` intents and
  the launcher, save in place, "Save a copy" via Storage Access Framework,
  discard-with-warning dialog on back press.
- **Document parsing (data layer)**:
  - DOCX via Apache POI into typed elements with rich span/table/image metadata.
  - ODT via a DOM-based parser and a deterministic hand-written ODT writer.
  - TXT via a streaming parser with a plain-text flattener for save.
  - MIME/extension detection with graceful fallback to plain text.
- **Architecture**: Clean Architecture (domain → data → UI) with Hilt DI,
  Room-backed recents, Flow-based state and an immutable editor document engine
  with bounded undo history.
- **Testing**: 160+ JVM unit tests covering span ops, editor operations,
  document transformation, undo, ViewModels, the Room recents DAO, the document
  repository, and all three parsers. Robolectric for Android-side tests.
- **Release hardening**: R8 minification with keep rules for POI/XMLBeans and
  `DocumentElement`; adaptive icon with monochrome layer; Room schema export;
  CI workflow running unit tests, lint, and a minified release build.

### Changed
- App name from "T Doc" to "TDoc".

### Fixed
- POI hex colors (`CTShd.fill`, `CTBorder.color`) previously surfaced as raw
  `byte[]` identity strings; now decoded back to `RRGGBB`.
- Image alt text now falls back to "Document Image" when the only description is
  POI's auto-populated file name.

[Unreleased]: https://github.com/Tosin-JD/TDoc/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Tosin-JD/TDoc/releases/tag/v0.1.0