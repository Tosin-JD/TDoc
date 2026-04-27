# TDoc Parser Architecture

## Overview

The TDoc parser is a **multi-format document parsing system** designed to extract structured document elements from DOCX and ODT files. The architecture follows a **strategy pattern** with format-specific implementations, allowing for extensible support of additional document formats.

---

## High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    DocumentParser (Interface)               │
│  - parse(inputStream) → Result<List<DocumentElement>>      │
│  - save(outputStream, content) → Result<Unit>              │
└─────────────────────────────────────────────────────────────┘
                              △
                              │
                    ┌─────────┴─────────┐
                    │                   │
         ┌──────────▼─────────┐ ┌──────▼─────────────┐
         │    DocxParser      │ │    OdtParser       │
         │  (MIME: docx)      │ │  (MIME: odt)       │
         └────────────────────┘ └────────────────────┘
                    │                   │
         ┌──────────▼──────────┐        │
         │   DocxPackage       │        │
         │  (ZIP extraction)   │        │
         └─────────────────────┘        │
                    │                   │
        ┌───────────┼───────────┐       │
        │           │           │       │
┌───────▼──────┐ ┌──▼─────────┐│┌─────▼────────────┐
│ Extractors   │ │Parsers     ││  OdtXmlParser    │
│ • Metadata   │ │ • Structure││  (XML Traversal) │
│ • Markup     │ │ • Paragraph││                  │
│ • EdgeCase   │ │ • Table    │└────────────────────┘
└──────────────┘ │ • List     │
                 │ • Image    │
                 │ • Field    │
                 │ • Drawing  │
                 │ • PageBreak│
                 │ • Style    │
                 └────────────┘
                       │
                       │
         ┌─────────────▼──────────────┐
         │   DocumentElement (Sealed) │
         │                            │
         │  • Paragraph               │
         │  • Table                   │
         │  • Image                   │
         │  • SectionHeader           │
         │  • Section                 │
         │  • HeaderFooter            │
         │  • Note                    │
         │  • Comment                 │
         │  • Bookmark                │
         │  • Field                   │
         │  • Metadata                │
         │  • Drawing                 │
         │  • EmbeddedObject          │
         │  • PageBreak               │
         └────────────────────────────┘
```

---

## Detailed Architecture Layers

### 1. **Factory Layer**

**`ParserFactory`** - Creates format-specific parsers based on MIME type

```kotlin
class ParserFactory(context: Context) {
    fun createParser(mimeType: String): DocumentParser?
}
```

- **MimeTypes.DOCX** → `DocxParser`
- **MimeTypes.ODT** → `OdtParser`
- **Default** → `null`

**Dependency Injection**: Uses Hilt @Inject for ApplicationContext

---

### 2. **Interface Layer**

**`DocumentParser`** - Main abstraction (Sealed Interface Pattern)

```kotlin
interface DocumentParser {
    suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>>
    suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit>
}
```

- **Suspend functions** ensure parsing runs off the main thread (Dispatchers.IO)
- **Result<T>** pattern for error handling

---

### 3. **Format-Specific Parsers**

#### **DOCX Parser Pipeline** (Most Complex)

```
Input DOCX File
      │
      ▼
┌──────────────────────────┐
│  DocxParser.parse()      │
│  1. Read bytes           │
│  2. Create XWPFDocument  │
│  3. Create DocxPackage   │
└──────────────────────────┘
      │
      ├─────────────────┬──────────────────┬──────────────────┐
      │                 │                  │                  │
      ▼                 ▼                  ▼                  ▼
  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────┐
  │ Structure   │  │ Extractors   │  │ Paragraph   │  │ Other       │
  │ Parser      │  │ (3 types)    │  │ Elements    │  │ Elements    │
  │             │  │              │  │             │  │             │
  │ • Document  │  │ • Metadata   │  │ • Text      │  │ • Fields    │
  │   level     │  │ • Markup     │  │   (spans)   │  │ • Drawings  │
  │ • Sections  │  │ • EdgeCase   │  │ • Lists     │  │ • Images    │
  │ • Headers   │  │              │  │ • Style     │  │ • PageBreak │
  │ • Footers   │  │              │  │ • Hyperlink │  │             │
  │ • Notes     │  │              │  │             │  │             │
  │ • Comments  │  │              │  │             │  │             │
  └─────────────┘  └──────────────┘  └─────────────┘  └─────────────┘
      │                 │                  │                  │
      └─────────────────┴──────────────────┴──────────────────┘
                        │
                        ▼
            ┌────────────────────────┐
            │  Unified Element List  │
            │  List<DocumentElement> │
            └────────────────────────┘
```

**Component Relationships** (DOCX):

```
DocxParser (main orchestrator)
├── DocxPackage
│   ├── ZIP extraction
│   ├── XML parsing
│   └── Relationship resolution
├── DocxStructureParser
│   ├── DocxParagraphParser
│   ├── DocxTableParser
│   └── Metadata extraction
├── DocxParagraphParser
│   ├── DocxListParser (list parsing)
│   ├── DocxParagraphStyleParser (styles)
│   └── Hyperlink extraction
├── DocxTableParser
│   └── Cell & row metadata
├── DocxImageParser (image extraction)
├── DocxFieldParser (field codes)
├── DocxDrawingParser (shapes/drawings)
├── DocxPageBreakParser (page breaks)
└── Extractors (implementations of DocxPackageExtractor)
    ├── DocxPackageMetadataExtractor
    ├── DocxAdvancedMarkupExtractor
    └── DocxEdgeCaseExtractor
```

#### **ODT Parser Pipeline** (Simpler)

```
Input ODT File
      │
      ▼
┌──────────────────────────┐
│  OdtParser.parse()       │
│  1. Read bytes           │
│  2. Extract ZIP          │
│  3. Find content.xml     │
└──────────────────────────┘
      │
      ▼
┌──────────────────────────┐
│  OdtXmlParser.parse()    │
│  1. Parse XML DOM        │
│  2. Traverse tree        │
│  3. Extract text nodes   │
└──────────────────────────┘
      │
      ▼
┌──────────────────────────┐
│ Supported Elements:      │
│  • <h> → SectionHeader   │
│  • <p> → Paragraph       │
└──────────────────────────┘
      │
      ▼
    Result<List<DocumentElement>>
```

---

### 4. **Data Models Layer**

#### **Main Output Model: DocumentElement**

A sealed class with 12 subtypes:

```kotlin
sealed class DocumentElement {
    // Content elements
    data class Paragraph(
        val spans: List<TextSpan>,
        val listLabel: String?,
        val style: ParagraphStyle,
        val hyperlink: HyperlinkInfo?,
        val listInfo: ListInfo?
    )
    
    data class Table(
        val rows: List<List<String>>,
        val hasHeader: Boolean,
        val metadata: TableMetadata
    )
    
    data class Image(
        val sourceUri: String,
        val altText: String?,
        val caption: String?
    )
    
    // Structural elements
    data class SectionHeader(val text: String, val level: Int)
    data class Section(val properties: SectionProperties)
    data class HeaderFooter(val content: HeaderFooterContent)
    
    // Annotation elements
    data class Note(val info: NoteInfo)
    data class Comment(val info: CommentInfo)
    data class Bookmark(val info: BookmarkInfo)
    data class Field(val info: FieldInfo)
    
    // Metadata elements
    data class Metadata(val info: MetadataInfo)
    data class Drawing(val info: DrawingInfo)
    data class EmbeddedObject(val info: EmbeddedObjectInfo)
    
    // Structural
    object PageBreak : DocumentElement()
}
```

#### **Support Data Models** (in `internal/models/`):

- **`TextSpan`**: Individual formatted text
  - Font properties (bold, italic, underline, etc.)
  - Color, size, language, shadow, outline, embossed, engraved
  
- **`ParagraphStyle`**: Paragraph formatting
  - Alignment, indentation, spacing
  
- **`TableMetadata`**: Table properties
  - Style, caption, borders, cell margins, cell-level metadata
  
- **`NoteInfo`**: Footnote/Endnote data
  
- **`CommentInfo`**: Document comments
  
- **`BookmarkInfo`**: Bookmark information
  
- **`FieldInfo`**: Field codes and instructions
  
- **`HyperlinkInfo`**: Link metadata
  
- **`HeaderFooterContent`**: Header/footer content
  
- **`DrawingInfo`**: Shape/drawing data
  
- **`EmbeddedObjectInfo`**: Embedded OLE objects

---

## Key Design Patterns

### 1. **Strategy Pattern** (Parser Selection)
- `DocumentParser` interface
- Concrete implementations: `DocxParser`, `OdtParser`
- Factory selection via MIME type

### 2. **Composition Pattern** (DOCX Pipeline)
- `DocxParser` composes multiple specialized parsers
- Each parser handles one responsibility:
  - `DocxParagraphParser` → paragraphs
  - `DocxTableParser` → tables
  - `DocxImageParser` → images
  - etc.

### 3. **Extractor Pattern** (Plugin Architecture)
- `DocxPackageExtractor` interface
- Multiple implementations for different extraction types:
  - Metadata extraction
  - Advanced markup extraction
  - Edge case handling

### 4. **Builder/Sealed Class Pattern** (Output Model)
- `DocumentElement` sealed class
- 12 specialized types for different content
- Type-safe at compile time

### 5. **Result Pattern** (Error Handling)
- `Result<T>` wrapper for success/failure
- No exception throwing from parsers
- Graceful degradation

### 6. **Suspension Pattern** (Async)
- `suspend fun` for off-thread execution
- `Dispatchers.IO` for blocking operations
- Memory efficient

---

## Data Flow Example (DOCX File)

```
File: document.docx (1.5 MB)
        │
        ▼ [FileInputStream]
   ParserFactory
        │ (detectMimeType: "application/vnd.ms-word")
        ▼
   DocxParser
        │
        ├─ Read bytes (1.5 MB)
        ├─ Create XWPFDocument
        ├─ Extract DocxPackage (ZIP metadata)
        │
        ├─ parseDocumentLevelElements()
        │  ├─ DocxStructureParser.parseSections()
        │  ├─ DocxStructureParser.parseHeadersAndFooters()
        │  ├─ DocxStructureParser.parseFootnotes()
        │  ├─ DocxStructureParser.parseEndnotes()
        │  └─ DocxStructureParser.parseComments()
        │
        ├─ Process body elements (XWPFParagraph, XWPFTable)
        │  ├─ For each paragraph:
        │  │  ├─ DocxParagraphParser (main text)
        │  │  ├─ DocxFieldParser (fields)
        │  │  ├─ DocxImageParser (images)
        │  │  ├─ DocxDrawingParser (shapes)
        │  │  └─ DocxPageBreakParser (breaks)
        │  │
        │  └─ For each table:
        │     └─ DocxTableParser
        │
        └─ Run extractors (Metadata, Markup, EdgeCase)
           │
           ▼
    List<DocumentElement> (normalized)
           │
           ├─ Paragraph[1..N]
           ├─ Table[1..M]
           ├─ Image[1..K]
           ├─ Metadata
           ├─ HeaderFooter
           ├─ Section
           ├─ Note
           ├─ Comment
           ├─ Bookmark
           ├─ Field
           ├─ Drawing
           └─ PageBreak
```

---

## Parsing Strategies

### DOCX Parsing Strategy
1. **Load** using Apache POI (XWPFDocument)
2. **Extract** package metadata (ZIP structure)
3. **Parse** structure (headers, footers, sections)
4. **Process** body elements (paragraphs, tables)
5. **Extract** content (text, images, drawings)
6. **Run** post-processing extractors
7. **Normalize** to `DocumentElement` list

### ODT Parsing Strategy
1. **Load** as ZIP
2. **Extract** content.xml
3. **Parse** XML to DOM
4. **Traverse** tree recursively
5. **Convert** elements to `DocumentElement`
6. **Return** element list

---

## Extension Points

### Adding a New Format (e.g., PDF)

1. **Create parser class**:
   ```kotlin
   class PdfParser : DocumentParser {
       override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>>
       override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit>
   }
   ```

2. **Register in ParserFactory**:
   ```kotlin
   fun createParser(mimeType: String): DocumentParser? {
       return when (mimeType) {
           MimeTypes.DOCX -> DocxParser(...)
           MimeTypes.ODT -> OdtParser(...)
           MimeTypes.PDF -> PdfParser(...)  // NEW
           else -> null
       }
   }
   ```

3. **Add MIME type**:
   ```kotlin
   object MimeTypes {
       const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
       const val ODT = "application/vnd.oasis.opendocument.text"
       const val PDF = "application/pdf"  // NEW
   }
   ```

### Adding New Document Elements

Extend `DocumentElement` sealed class:
```kotlin
data class TextBox(val text: String, val position: Position) : DocumentElement()
```

---

## Key Implementation Details

### DOCX Package Extraction
- Uses Apache POI library (XWPFDocument)
- ZIP-based format with XML metadata
- Handles relationships (links, embedded content)
- Extracts media from media/ folder

### Image Handling
- Extracted to cache directory
- Referenced by URI in `Image.sourceUri`
- Supports embedded and linked images

### Metadata Preservation
- Styles, colors, fonts captured in `TextSpan`
- Paragraph properties in `ParagraphStyle`
- Table metadata in `TableMetadata`
- Hyperlinks in `HyperlinkInfo`

### List Handling
- `DocxListParser` extracts list labels
- Preserves nested list structure
- List info metadata available

### Field Extraction
- Captures field codes (mergefields, etc.)
- Stores instructions and results

### Asynchronous Processing
- All parsing via `suspend fun`
- Runs on `Dispatchers.IO`
- Non-blocking main thread

---

## Performance Characteristics

| Operation | DOCX | ODT |
|-----------|------|-----|
| Parse time | O(n) where n=elements | O(n) where n=XML nodes |
| Memory usage | ~2-3x file size (POI buffering) | ~1.5x file size |
| Lazy loading | No (full parse) | No (full parse) |
| Streaming | Not supported | Possible (not implemented) |

---

## Error Handling

- All parsers wrap results in `Result<T>`
- Exceptions caught and returned as failures
- Input streams properly closed in finally blocks
- Invalid formats return `Result.failure(exception)`

---

## Testing Strategy

- Unit tests for individual parsers
- Integration tests with sample documents
- Edge case handling (corrupted files, unusual formatting)
- Memory leak testing for large documents

