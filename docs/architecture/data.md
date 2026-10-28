# Data Layer Overview

The data layer handles file I/O, document parsing, and persistence. It converts between external document formats (DOCX, ODT) and the internal `DocumentElement` model.

---

## Module Overview

```
data/
├── repository/             ← Data repositories
│   ├── DocumentRepository.kt
│   └── DocumentRepositoryImpl.kt
├── parser/                 ← Document parsers
│   ├── ParserFactory.kt
│   ├── DocumentParser.kt
│   ├── DocxParser.kt
│   ├── OdtParser.kt
│   └── mapper/             ← XML → DocumentElement mapping
├── data/                   ← Raw data handling
├── diagnostics/            ← Parse diagnostics
└── exception/              ← Parse exceptions
```

---

## Repository Pattern

The `DocumentRepository` is the single entry point for file operations:

```kotlin
interface DocumentRepository {
    suspend fun readDocumentFromUri(uri: Uri): Result<DocumentReadResult>
    suspend fun saveDocumentToUri(uri: Uri, elements: List<DocumentElement>)
    suspend fun loadDocument(filePath: String): Flow<Document>
    suspend fun saveDocument(document: Document)
    fun createNewDocument(uri: Uri)
    fun getFileName(uri: Uri): String
}
```

### Implementation

`DocumentRepositoryImpl` uses Hilt and Android's `ContentResolver`:

```kotlin
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val docxParser: DocxParser,
    private val odtParser: OdtParser,
    private val recentFileDao: RecentFileDao
) : DocumentRepository {
    override suspend fun readDocumentFromUri(uri: Uri): Result<DocumentReadResult> = runCatching {
        // 1. Open content resolver stream
        // 2. Select parser based on file extension
        // 3. Parse XML → DocumentElement tree
        // 4. Resolve styles
        // 5. Return DocumentReadResult
    }
}
```

---

## Parser Architecture

The parser layer follows a **factory + adapter** pattern:

```
ParserFactory
  ├─> DocxParser (DOCX)
  │    ├─> DocxDocumentParser (high-level)
  │    │    ├─> DocxBlockMapper
  │    │    ├─> DocxParagraphMapper
  │    │    ├─> DocxTableMapper
  │    │    └─> DocxRunMapper (inline)
  │    └─> DocxNumberingParser
  │
  └─> OdtParser (ODT)
       ├─> OdtDocumentParser
       ├─> OdtParagraphParser
       ├─> OdtTableParser
       └─> OdtImageParser
```

### Key Files

| File | Purpose |
|---|---|
| `ParserFactory.kt` | Selects parser based on file extension |
| `DocumentParser.kt` | Parse a stream of XML into `List<DocumentElement>` |
| `DocxParser.kt` | Parse DOCX (XMLPullParser on OPC package) |
| `OdtParser.kt` | Parse ODT (XMLPullParser on ZIP stream) |
| `DocxDocumentParser.kt` | High-level DOCX structure |
| `OdtDocumentParser.kt` | High-level ODT structure |
| `OdtParagraphParser.kt` | Parse paragraph with spans, styles, list info |
| `OdtTableParser.kt` | Parse table rows, cells, grid |

---

## Data Flow (Open Document)

```
User picks file (uri)
  │
  ▼
EditorScreen → viewModel.onFilePicked(uri)
  │
  ▼
DocumentRepositoryImpl.readDocumentFromUri(uri)
  │
  ├── 1. Open contentResolver.openInputStream(uri)
  ├── 2. Parse XML stream (DocxParser or OdtParser)
  ├── 3. Map XML to DocumentElement tree (mapper chain)
  ├── 4. Resolve styles and numbering
  └── 5. Return DocumentReadResult
  │
  ▼
ViewModel:
  documentElements = result.content
  _currentDocument.value = DocumentData(...)
  syncDocumentState()
  _events.emit("Opened: fileName")
  │
  ▼
UI: EditorScreen recomposes
  │
  ▼
PrintLayout: paginator.paginate() → List<PageModel>
```

---

## Key Data Types

### DocumentData

```kotlin
data class DocumentData(
    val id: String = "",
    val filename: String = "",
    val content: List<DocumentElement> = emptyList(),
    val format: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
```

### DocumentElement (sealed interface)

```kotlin
sealed interface DocumentElement {
    val id: String
    
    data class Paragraph(
        override val id: String,
        val spans: List<TextSpan>,
        val listLabel: String? = null,
        val style: ParagraphStyle = ParagraphStyle(),
        val hyperlink: HyperlinkInfo? = null,
        val listInfo: ListInfo? = null
    ) : DocumentElement
    
    data class Table(
        override val id: String,
        val rows: List<List<String>>,
        val hasHeader: Boolean = false,
        val metadata: TableMetadata = TableMetadata(),
        val richRows: List<List<RichTableCell>> = emptyList(),
        val gridColumnWidthsPt: List<Float> = emptyList()
    ) : DocumentElement
    
    data class Image(
        override val id: String,
        val sourceUri: String,
        val altText: String?,
        val caption: String? = null
    ) : DocumentElement
    
    data class SectionHeader(
        override val id: String,
        val text: String,
        val level: Int = 1,
        val style: ParagraphStyle = ParagraphStyle(),
        val headingFontSizePoints: Float? = null
    ) : DocumentElement
    
    // ... other element types
}
```

### HeaderFooter

```kotlin
data class HeaderFooter(
    override val id: String,
    val content: HeaderFooterContent
)

data class HeaderFooterContent(
    val kind: HeaderFooterKind,  // HEADER or FOOTER
    val text: String,
    val variant: String = "default"  // "default", "first", "even"
)
```

---

## Style Resolution

Styles are resolved from the document's style definitions:

```
Document style definition (e.g., "Heading1", "Normal")
  │
  ▼
Style resolver (DocxStyleResolver / OdfStyleResolver)
  │
  ▼
ParagraphStyle / TextStyle / TableStyle / ListStyle
  │
  ▼
Applied to DocumentElement during parsing
  │
  ▼
Cascaded: paragraph style → run-level override
```

### ParagraphStyle

```kotlin
data class ParagraphStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val alignment: ParagraphAlignment = ParagraphAlignment.START,
    val indentation: ParagraphIndentation = ParagraphIndentation(),
    val spacing: ParagraphSpacing = ParagraphSpacing(),
    val isHeading: Boolean = false,
    val headingLevel: Int? = null,
    val pageBreakBefore: Boolean = false,
    val keepWithNext: Boolean = false,
    val keepLinesTogether: Boolean = false
)
```

### TextSpan

```kotlin
data class TextSpan(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val isHidden: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val color: String = "000000",
    val language: String? = null,
    // ...
) : Comparable<TextSpan>
```

---

## Localization

TDoc stores locale-aware content in XML attributes. During parsing, locale tags are resolved to `TextSpan.language`:

```xml
<w:r>
  <w:rPr>
    <w:lang w:val="en-US" w:eastAsia="zh-CN" w:bidi="ar-SA"/>
  </w:rPr>
  <w:t>Hello</w:t>
</w:r>
```

Maps to:

```kotlin
TextSpan(
    text = "Hello",
    language = "en-US",
    // ...
)
```

---

## Testing

The data layer is tested with:

| Test Type | Coverage |
|---|---|
| Unit tests | Parser logic (mock XML input) |
| Instrumentation tests | Full document round-trip |

Example: `OdtRichParserTest.kt` tests full document parsing including styles, lists, tables, and images.

---

## Migration Notes

### Future: Pure Data Layer

The data layer is already **platform-independent** in theory — it only depends on `Context` for file I/O. The `DocumentElement` tree is fully portable. A future migration could:

1. Extract `domain/` package into a pure Kotlin module
2. Move `data/` into the Android app only
3. Make tests run on the JVM without Android

### Current Limitations

- `DocumentElement` is in `domain/model/` but is coupled to Android types (e.g., `Log`)
- `ParserFactory` creates parser instances directly (no DI)
- `DocumentRepositoryImpl` uses `ContentResolver` directly
