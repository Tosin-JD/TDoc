---
name: odt-parser
description: >
  Build, extend, or debug an ODT (OpenDocument Text) file parser in Kotlin.
  Use this skill whenever the user wants to parse .odt files, extract structured
  content from ODT documents, implement OdtParser or OdtXmlParser classes, add
  new DocumentElement types for ODT output, integrate ODT parsing into a
  multi-format document pipeline, or handle ODT-specific ZIP/XML structures.
  Trigger this skill even when the user only mentions "OpenDocument", "LibreOffice
  files", "content.xml parsing", or wants to extend an existing parser to support ODT.
---

# ODT Parser — Kotlin Implementation Skill

This skill guides you through building a production-quality ODT parser in Kotlin
that integrates with a `DocumentParser` interface and outputs a normalized
`List<DocumentElement>`.

---

## Architecture Overview

An ODT file is a ZIP archive containing XML files. The parsing pipeline is:

```
.odt file (ZIP)
    └── content.xml        ← primary content
    └── styles.xml         ← named styles
    └── meta.xml           ← document metadata
    └── Pictures/          ← embedded images

Pipeline:
  OdtParser                (entry point, implements DocumentParser)
      └── OdtZipExtractor  (unzips, locates content.xml)
          └── OdtXmlParser (DOM traversal, element mapping)
              └── List<DocumentElement>
```

**Key constraint**: ODT uses the ODF namespace (`urn:oasis:names:tc:opendocument:xmlns:*`).
Always resolve namespace-prefixed element names — never match on local names alone.

---

## Namespace Reference

| Prefix | URI                                                          | Used for              |
|--------|--------------------------------------------------------------|-----------------------|
| `text` | `urn:oasis:names:tc:opendocument:xmlns:text:1.0`            | Paragraphs, headings  |
| `draw` | `urn:oasis:names:tc:opendocument:xmlns:drawing:1.0`         | Images, shapes        |
| `table`| `urn:oasis:names:tc:opendocument:xmlns:table:1.0`           | Tables                |
| `fo`   | `urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0`| Formatting properties |
| `style`| `urn:oasis:names:tc:opendocument:xmlns:style:1.0`           | Style definitions     |
| `xlink`| `http://www.w3.org/1999/xlink`                              | Hyperlink href        |
| `meta` | `urn:oasis:names:tc:opendocument:xmlns:meta:1.0`            | Document metadata     |

---

## Step-by-Step Implementation Guide

### Step 1 — Implement `OdtParser` (Entry Point)

`OdtParser` implements `DocumentParser`. It must:

1. Accept an `InputStream`
2. Read all bytes (copy to `ByteArrayInputStream` so the stream is re-readable)
3. Delegate ZIP extraction to `OdtZipExtractor`
4. Delegate XML parsing to `OdtXmlParser`
5. Return `Result<List<DocumentElement>>`

**Checklist:**
- [ ] Class annotated with `@Inject constructor` for Hilt DI
- [ ] Both `parse()` and `save()` are `suspend fun`
- [ ] Use `withContext(Dispatchers.IO)` internally
- [ ] Wrap entire body in `runCatching { }` and return as `Result`
- [ ] Close streams in `finally` blocks

**`save()` guidance**: ODT write-back is complex. For an initial implementation,
return `Result.failure(UnsupportedOperationException("ODT save not yet implemented"))`.

---

### Step 2 — Implement `OdtZipExtractor`

Responsibility: open the ZIP, find named entries, return their byte content.

**Required methods:**
```
extractEntry(zipBytes: ByteArray, entryName: String): ByteArray?
listEntries(zipBytes: ByteArray): List<String>
```

**Rules:**
- Use `java.util.zip.ZipInputStream` (available on Android without extra deps)
- Entry names are case-sensitive; `content.xml` ≠ `Content.xml`
- If `content.xml` is missing → throw `IllegalArgumentException("Not a valid ODT file: missing content.xml")`
- For images, look inside `Pictures/` entries; preserve original entry name as the image URI

---

### Step 3 — Implement `OdtXmlParser`

Responsibility: parse `content.xml` bytes into `List<DocumentElement>`.

**Parsing approach — DOM (recommended for ODT):**
- Use `javax.xml.parsers.DocumentBuilderFactory` (built-in, no extra dependencies)
- Set `isNamespaceAware = true` on the factory — **this is mandatory**
- Get the `<office:body>` → `<office:text>` subtree as the root of traversal
- Recursively walk child nodes

**Element mapping table:**

| ODT XML element        | `DocumentElement` subtype | Notes                                      |
|------------------------|---------------------------|--------------------------------------------|
| `<text:h>`             | `SectionHeader`           | `text:outline-level` attr → `level`        |
| `<text:p>`             | `Paragraph`               | May contain spans, links, images           |
| `<text:span>`          | contributes to `TextSpan` | Nested inside paragraph                    |
| `<text:list>`          | `Paragraph` (list item)   | Set `listLabel` from `<text:list-item>`    |
| `<table:table>`        | `Table`                   | Rows = `<table:table-row>`, cells = `<table:table-cell>` |
| `<draw:image>`         | `Image`                   | `xlink:href` → `sourceUri`                 |
| `<text:note>`          | `Note`                    | `text:note-class` = "footnote"/"endnote"   |
| `<text:bookmark>`      | `Bookmark`                | `text:name` attr → `BookmarkInfo.name`     |
| `<text:a>`             | `HyperlinkInfo` on span   | `xlink:href` attr                          |
| `<text:page-break>`    | `PageBreak`               | Or detected via paragraph style            |

**Traversal rules:**
- Use a depth-first recursive walk
- Skip `#text` nodes that are pure whitespace when building structural elements
- When a `<text:p>` contains no meaningful text AND no child elements → skip it (empty paragraph)
- Mixed content (text + `<text:span>` + `<text:a>`) must be collected in order into `List<TextSpan>`

---

### Step 4 — Text Span Extraction

Inside a `<text:p>` or `<text:h>`, text is assembled from:

1. **Direct text nodes** → `TextSpan` with no formatting override
2. **`<text:span>`** → `TextSpan` with style from `text:style-name` attr
3. **`<text:a>`** → `TextSpan` with `HyperlinkInfo(href = xlink:href)`
4. **`<text:s>`** → space(s); `text:c` attr = count (default 1)
5. **`<text:tab>`** → tab character `\t`
6. **`<text:line-break>`** → newline `\n`

**Style resolution for `TextSpan`:**
- Look up `text:style-name` in the automatic styles from `<office:automatic-styles>`
- Then fall back to named styles in `styles.xml` (load this file too)
- Extract `fo:font-weight="bold"`, `fo:font-style="italic"`, `style:text-underline-style`, `fo:color`

---

### Step 5 — Table Extraction

```
<table:table>
  <table:table-column />    ← skip (column metadata only)
  <table:table-row>
    <table:table-cell>
      <text:p>cell text</text:p>
    </table:table-cell>
  </table:table-row>
</table:table>
```

**Rules:**
- Collect all `<table:table-row>` children
- For each row, collect all `<table:table-cell>` children
- Cell text = recursively extract all text content from the cell's child paragraphs (concatenate with `\n` if multiple paragraphs)
- `hasHeader`: check if first row's cells have `table:style-name` containing "Header" — otherwise `false`
- Respect `table:number-columns-spanned` attr for merged cells: repeat the cell value that many times in the row list

---

### Step 6 — Image Extraction

```
<draw:frame draw:name="Image1" ...>
  <draw:image xlink:href="Pictures/image1.png" xlink:type="simple"/>
  <svg:desc>alt text here</svg:desc>
</draw:frame>
```

- `sourceUri` = `xlink:href` value (e.g. `"Pictures/image1.png"`)
- `altText` = text content of `<svg:desc>` or `<draw:object-ole>` title, if present
- Extract the image bytes from the ZIP using `OdtZipExtractor.extractEntry(zipBytes, sourceUri)`
- Cache to a temp file; replace `sourceUri` with the `file://` URI of the cached file

---

### Step 7 — Metadata Extraction

Parse `meta.xml` for document-level `Metadata`:

| XML element                    | MetadataInfo field    |
|--------------------------------|-----------------------|
| `<meta:initial-creator>`       | `author`              |
| `<dc:title>`                   | `title`               |
| `<dc:description>`             | `description`         |
| `<meta:creation-date>`         | `createdAt`           |
| `<dc:date>`                    | `modifiedAt`          |
| `<meta:word-count>`            | `wordCount`           |

Add the `Metadata` element as the **first** item in the result list.

---

### Step 8 — Register in `ParserFactory`

```kotlin
MimeTypes.ODT -> OdtParser(context)
```

Add to `MimeTypes`:
```kotlin
const val ODT = "application/vnd.oasis.opendocument.text"
```

---

## Error Handling Patterns

| Scenario                          | Correct Response                                                   |
|-----------------------------------|--------------------------------------------------------------------|
| Not a ZIP / corrupted archive     | `Result.failure(IllegalArgumentException("Invalid ODT file"))`    |
| Missing `content.xml`             | `Result.failure(IllegalArgumentException("Missing content.xml"))` |
| Unknown XML element               | Skip silently, continue traversal                                  |
| Image entry not found in ZIP      | Set `sourceUri = xlink:href` raw value; log a warning             |
| Null or empty `text:outline-level`| Default `SectionHeader.level = 1`                                  |
| Malformed namespace               | Fall back to local name matching with a logged warning             |

---

## Testing Strategy

### Unit Tests

| Test class              | What to test                                                              |
|-------------------------|---------------------------------------------------------------------------|
| `OdtZipExtractorTest`   | Valid ZIP, missing entry, corrupted ZIP, entry name case sensitivity      |
| `OdtXmlParserTest`      | Headings at levels 1–6, empty paragraphs, nested spans, lists, tables, images |
| `OdtParserTest`         | Full parse of a real `.odt` fixture, Result.isSuccess, element count     |

### Test Fixture Files to Prepare

- `simple.odt` — only headings and paragraphs
- `formatted.odt` — bold, italic, underline, color spans
- `with_table.odt` — single table, merged cells
- `with_image.odt` — one embedded PNG
- `with_list.odt` — ordered and unordered lists
- `empty.odt` — no body content (edge case)
- `corrupted.odt` — invalid ZIP (should return `Result.failure`)

### Assertions Checklist

- [ ] `result.isSuccess` is `true` for valid files
- [ ] First element is `DocumentElement.Metadata`
- [ ] `SectionHeader.level` matches `text:outline-level`
- [ ] `Table.rows` has correct row and column count
- [ ] `Image.sourceUri` is a `file://` URI pointing to a cached file
- [ ] `Paragraph.spans` preserves order and formatting flags
- [ ] Empty ODT returns `Result.success(listOf(metadata))` — not failure

---

## Common Pitfalls

1. **Forgetting `isNamespaceAware = true`** — element lookups by local name will appear to work but will miss namespaced attrs like `xlink:href`.
2. **Matching element names without prefix** — use `localName` + `namespaceURI` checks, not `nodeName` (which includes prefix and is implementation-dependent).
3. **Not handling `<text:s text:c="N">`** — produces garbled whitespace in output.
4. **Assuming content.xml root is `<office:document>`** — it may be `<office:document-content>` in some editors; match both.
5. **Reading the ZIP stream twice** — `ZipInputStream` is forward-only; buffer all entries on first pass into a `Map<String, ByteArray>`.
6. **Blocking on main thread** — always wrap in `withContext(Dispatchers.IO)` inside suspend functions.

---

## Dependencies

No external libraries are required beyond what is already available on Android/JVM:

| Need                  | Use                                       |
|-----------------------|-------------------------------------------|
| ZIP reading           | `java.util.zip.ZipInputStream`            |
| XML parsing           | `javax.xml.parsers.DocumentBuilderFactory`|
| Coroutines            | `kotlinx.coroutines` (already in project) |
| DI                    | Hilt (already in project)                 |

> If richer style parsing is needed later, consider **Apache ODF Toolkit** (`org.odftoolkit:odfdom-java`), but avoid it for the initial implementation to keep the dependency footprint small.

---

## Integration Checklist

- [ ] `OdtParser` implements `DocumentParser`
- [ ] `OdtParser` registered in `ParserFactory` under `MimeTypes.ODT`
- [ ] `OdtZipExtractor` correctly buffers all ZIP entries on first pass
- [ ] `OdtXmlParser` parses with namespace awareness
- [ ] All `DocumentElement` subtypes listed in the mapping table are handled
- [ ] `save()` returns `UnsupportedOperationException` until implemented
- [ ] All parsers wrapped in `Result` — no uncaught exceptions escape
- [ ] Unit tests cover all fixture files listed above
- [ ] No parsing work runs on the main thread