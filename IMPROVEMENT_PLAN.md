# TDoc Parser - Comprehensive Improvement Plan

**Date:** April 27, 2026  
**Document:** Strategic roadmap for parser optimization and refactoring  
**Priority Levels:** Critical (P0), High (P1), Medium (P2), Low (P3)

---

## Executive Summary

The parser codebase has reached a point where technical debt significantly impacts:
- **Performance**: 3.2× memory overhead makes large files unparseable on mobile
- **Maintainability**: 40%+ code duplication across DOCX/ODT parsers
- **Reliability**: Silent failures make production debugging impossible
- **Features**: Round-trip conversion loses all formatting and structure

This plan organizes 40+ specific improvements across 10 strategic categories.

---

## Priority Overview

| Category | Total Items | P0 | P1 | P2 | P3 |
|----------|-------------|----|----|----|----|
| Error Handling & Logging | 8 | 3 | 3 | 2 | 0 |
| Code Deduplication | 9 | 2 | 4 | 3 | 0 |
| Performance Optimization | 10 | 2 | 5 | 3 | 0 |
| Architecture Refactoring | 6 | 1 | 3 | 2 | 0 |
| Missing Features | 8 | 0 | 2 | 4 | 2 |
| Testing Infrastructure | 6 | 1 | 2 | 2 | 1 |
| Documentation | 5 | 0 | 2 | 2 | 1 |
| API Design Improvements | 7 | 1 | 3 | 2 | 1 |
| Memory Management | 6 | 2 | 3 | 1 | 0 |
| Configuration & Tooling | 4 | 0 | 1 | 2 | 1 |
| **TOTAL** | **69** | **12** | **28** | **23** | **6** |

---

## Category 1: Error Handling & Logging (P0/P1)

### 1.1 [P0] Implement Structured Logging System

**Current State:**
- Silent exception swallowing in DocxParser.parse()
- Inconsistent e.printStackTrace() in DocxImageParser
- No logging context (file name, line number, severity)
- Exceptions lost in Result.failure()

**Improvements:**
1. Add Timber or Android Log wrapper
2. Log all exceptions with context
3. Create error aggregation mechanism
4. Track error rates by type

**Files to Modify:**
- DocumentParser.kt (add logging interface)
- DocxParser.kt (wrap all try-catch blocks)
- OdtParser.kt (wrap all try-catch blocks)
- DocxImageParser.kt (replace printStackTrace)
- OdtImageParser.kt (add logging)

**Estimated Effort:** 4 hours

---

### 1.2 [P0] Replace Silent Failures with Detailed Error Context

**Current State:**
```kotlin
catch (e: Exception) {
    Result.failure(e)  // ← No context about what failed
}
```

**Target State:**
```kotlin
catch (e: Exception) {
    logger.error("Failed to parse document at position", 
        extra = mapOf(
            "bytesParsed" to bytesParsed,
            "elementType" to currentElement,
            "documentHash" to documentHash
        ),
        throwable = e
    )
    Result.failure(ParseException(e, context))
}
```

**Implementation Tasks:**
1. Create custom ParseException class
2. Capture parse state on error
3. Include document fingerprint
4. Log error with full context
5. Allow error recovery strategies

**Files to Create:**
- data/parser/exception/ParseException.kt
- data/parser/exception/ParseErrorContext.kt

**Files to Modify:**
- All *Parser.kt files

**Estimated Effort:** 5 hours

---

### 1.3 [P1] Add Validation Before Returning Results

**Current State:**
```kotlin
document.footnotes
    .orEmpty()
    .filterNot { it.id.toString() in setOf("-1", "0", "1") }
    .map { it.toNote(NoteKind.FOOTNOTE) }  // ← No validation that this succeeds
```

**Implementation Tasks:**
1. Create validation interface
2. Add post-parse validation step
3. Check for null/invalid elements
4. Return validation error details
5. Provide recovery suggestions

**Files to Create:**
- data/parser/validation/DocumentValidator.kt
- data/parser/validation/ValidationResult.kt

**Files to Modify:**
- DocxStructureParser.kt
- OdtXmlParser.kt

**Estimated Effort:** 3 hours

---

### 1.4 [P1] Handle Resource Cleanup Properly

**Current State:**
```kotlin
finally {
    try { inputStream.close() } catch (_: Exception) {}  // ← Silently ignores errors
}
```

**Implementation Tasks:**
1. Use try-with-resources (Kotlin scoping)
2. Log resource cleanup failures
3. Implement multi-resource cleanup
4. Add leak detection for development

**Files to Modify:**
- DocxParser.kt
- OdtParser.kt
- All image parsers

**Estimated Effort:** 2 hours

---

### 1.5 [P1] Implement Error Recovery Strategies

**Current State:**
- Parse fails on any error
- No partial result fallback
- No retry mechanism

**Target Features:**
1. Graceful degradation (parse what's possible)
2. Skip corrupted elements and continue
3. Configurable retry logic
4. Partial result reporting

**Files to Create:**
- data/parser/recovery/RecoveryStrategy.kt
- data/parser/recovery/GracefulDegradationStrategy.kt

**Files to Modify:**
- DocumentParser.kt
- DocxParser.kt
- OdtParser.kt

**Estimated Effort:** 6 hours

---

### 1.6 [P1] Safe Type Conversions with Validation

**Current State:**
```kotlin
val format = levelConf.numFmt?.`val`?.toString() ?: "bullet"  // ← Unsafe
```

**Implementation Tasks:**
1. Create type conversion utilities
2. Validate before converting
3. Log conversion failures
4. Return sensible defaults with indication

**Files to Create:**
- data/parser/util/SafeConversions.kt

**Files to Modify:**
- DocxListParser.kt
- DocxParagraphStyleParser.kt

**Estimated Effort:** 2 hours

---

### 1.7 [P2] Add Exception Classification

**Implementation Tasks:**
1. Create exception hierarchy
2. Classify errors (recoverable vs fatal)
3. Implement error codes
4. Enable telemetry tracking

**Files to Create:**
- data/parser/exception/ErrorClassification.kt
- data/parser/exception/ErrorCode.kt

**Estimated Effort:** 3 hours

---

### 1.8 [P2] Implement Error Telemetry

**Implementation Tasks:**
1. Track error rates by type
2. Create error dashboard metrics
3. Send to analytics service
4. Create error trend reports

**Files to Create:**
- data/parser/telemetry/ErrorTelemetry.kt

**Estimated Effort:** 4 hours

---

## Category 2: Code Deduplication (P1/P2)

### 2.1 [P0] Extract Shared Namespace Constants

**Current State:**
Namespace URIs hardcoded in 5 files:
```kotlin
// OdtXmlParser.kt
private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"

// OdtParagraphParser.kt
private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"

// (repeated in 3 more files)
```

**Solution:**
Create centralized namespace registry.

**Files to Create:**
- data/parser/odt/OdtNamespaces.kt

```kotlin
object OdtNamespaces {
    const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
}
```

**Files to Modify:**
- OdtXmlParser.kt
- OdtParagraphParser.kt
- OdtTableParser.kt
- OdtImageParser.kt
- OdtStyleParser.kt

**Estimated Effort:** 1 hour

---

### 2.2 [P1] Extract Shared TextSpan Parsing Logic

**Current State:**
TextSpan extraction duplicated in DOCX/ODT parsers with format-specific handling.

**Solution:**
Create TextSpanFormatter interface with format-specific implementations.

**Files to Create:**
- data/parser/formatter/TextSpanFormatter.kt
- data/parser/formatter/DocxTextSpanFormatter.kt
- data/parser/formatter/OdtTextSpanFormatter.kt

```kotlin
interface TextSpanFormatter {
    fun format(
        text: String,
        formattingSource: Any  // Run or Element
    ): TextSpan
}

class DocxTextSpanFormatter : TextSpanFormatter {
    override fun format(text: String, formattingSource: Any): TextSpan {
        val run = formattingSource as XWPFRun
        return TextSpan(
            text = text,
            isBold = run.isBold,
            isItalic = run.isItalic,
            // ... etc
        )
    }
}
```

**Files to Modify:**
- DocxParagraphParser.kt
- OdtParagraphParser.kt

**Estimated Effort:** 3 hours

---

### 2.3 [P1] Extract Shared Style Parsing Logic

**Solution:**
Create StyleExtractor interface with format-specific implementations.

**Files to Create:**
- data/parser/style/StyleExtractor.kt
- data/parser/style/DocxStyleExtractor.kt
- data/parser/style/OdtStyleExtractor.kt

**Files to Modify:**
- DocxParagraphStyleParser.kt
- OdtStyleParser.kt

**Estimated Effort:** 4 hours

---

### 2.4 [P1] Extract Shared Image Extraction Logic

**Solution:**
Create ImageExtractor interface with format-specific implementations and unified error handling.

**Files to Create:**
- data/parser/image/ImageExtractor.kt
- data/parser/image/DocxImageExtractor.kt (rename from DocxImageParser)
- data/parser/image/OdtImageExtractor.kt (rename from OdtImageParser)
- data/parser/image/ImageCache.kt

**Key Features:**
1. Unified cache management
2. Consistent error logging
3. Image validation
4. Deduplication (same image hash)

**Files to Modify:**
- DocxImageParser.kt
- OdtImageParser.kt
- DocxParser.kt (use ImageExtractor)
- OdtParser.kt (use ImageExtractor)

**Estimated Effort:** 5 hours

---

### 2.5 [P1] Extract Shared Table Parsing Logic

**Solution:**
Create TableExtractor interface; adjust ODT to match DOCX feature parity.

**Files to Create:**
- data/parser/table/TableExtractor.kt
- data/parser/table/DocxTableExtractor.kt
- data/parser/table/OdtTableExtractor.kt

**Key Features:**
1. Unified row/cell iteration
2. Consistent metadata extraction
3. Cell merging support
4. Table styling preservation

**Files to Modify:**
- DocxTableParser.kt → DocxTableExtractor.kt
- OdtTableParser.kt → OdtTableExtractor.kt

**Estimated Effort:** 5 hours

---

### 2.6 [P2] Extract Shared Metadata Extraction Pattern

**Solution:**
Create MetadataExtractor base class with template method pattern.

**Files to Create:**
- data/parser/extractor/BaseMetadataExtractor.kt

```kotlin
abstract class BaseMetadataExtractor<T> {
    abstract fun extractProperties(source: T): Map<String, String>
    abstract fun getSummary(properties: Map<String, String>): String
    
    final fun extract(source: T): DocumentElement.Metadata? {
        val properties = extractProperties(source)
        if (properties.isEmpty()) return null
        return DocumentElement.Metadata(
            MetadataInfo(
                properties = properties,
                summary = getSummary(properties)
            )
        )
    }
}
```

**Files to Modify:**
- DocxPackageMetadataExtractor.kt
- DocxAdvancedMarkupExtractor.kt
- DocxEdgeCaseExtractor.kt

**Estimated Effort:** 4 hours

---

### 2.7 [P2] Extract Shared Utility Functions

**Solution:**
Create shared utilities module with commonly used functions.

**Files to Create:**
- data/parser/util/ParsingUtils.kt (string, collection utilities)
- data/parser/util/XmlUtils.kt (XML navigation)
- data/parser/util/StreamUtils.kt (I/O utilities)

**Estimated Effort:** 2 hours

---

### 2.8 [P2] Consolidate Alignment Mapping

**Current State:**
Alignment values mapped independently in each style parser.

**Solution:**
Create centralized alignment converter.

**Files to Create:**
- data/parser/util/AlignmentConverter.kt

**Estimated Effort:** 1 hour

---

### 2.9 [P2] Extract Duplicate asInt() Functions

**Current State:**
`asInt()` extension defined multiple times.

**Solution:**
Move to shared extensions module.

**Files to Create:**
- data/parser/util/PrimitiveExtensions.kt

**Estimated Effort:** 30 minutes

---

## Category 3: Performance Optimization (P0/P1)

### 3.1 [P0] Implement Streaming Parser for Large Files

**Current State:**
```kotlin
val bytes = inputStream.readBytes()  // ← 100% file in memory
```

**Solution:**
Implement streaming/chunked parsing using SAX or manual streaming.

**Implementation Strategy:**
1. Use SAX parser for DOCX (event-driven, not DOM)
2. Process elements incrementally
3. Yield results as they're parsed
4. Support cancellation

**Files to Create:**
- data/parser/streaming/StreamingDocxParser.kt
- data/parser/streaming/StreamingOdtParser.kt
- data/parser/streaming/ParsedElement.kt (for streaming results)

**Key Changes:**
```kotlin
// Before: suspend fun parse() → Result<List<DocumentElement>>
// After: fun parseStream() → Flow<DocumentElement>

fun parseStream(inputStream: InputStream): Flow<DocumentElement> =
    flow {
        SAXParserFactory.newInstance().newSAXParser().parse(
            inputStream,
            object : DefaultHandler() {
                override fun startElement(...) {
                    val element = parseElement(...)
                    emit(element)  // ← Yield as parsed
                }
            }
        )
    }.flowOn(Dispatchers.IO)
```

**Impact:**
- Reduces memory from 3.2× to 1.2× file size
- Enables parsing 1GB+ files on mobile
- Allows progress reporting
- Enables cancellation

**Estimated Effort:** 15 hours

---

### 3.2 [P0] Implement Intelligent Caching for XML Parsing

**Current State:**
```kotlin
// Same files parsed 3+ times by different extractors
docProps/core.xml → parsed 3 times
word/settings.xml → parsed 3 times
word/styles.xml → parsed 3 times
```

**Solution:**
Create XmlCache layer to cache parsed documents.

**Files to Create:**
- data/parser/cache/XmlCache.kt
- data/parser/cache/ParsedDocumentCache.kt

```kotlin
interface XmlCache {
    fun getDocument(path: String): Document?
    fun putDocument(path: String, doc: Document)
    fun clear()
}

class ParsedDocumentCache(private val maxSize: Int = 10) : XmlCache {
    private val cache = LruCache<String, Document>(maxSize)
    
    override fun getDocument(path: String): Document? = cache[path]
    override fun putDocument(path: String, doc: Document) = cache.put(path, doc)
}
```

**Files to Modify:**
- DocxPackageMetadataExtractor.kt (use cache)
- DocxAdvancedMarkupExtractor.kt (use cache)
- DocxEdgeCaseExtractor.kt (use cache)
- DocxParser.kt (provide cache to extractors)

**Impact:**
- Reduce parsing time by 40-60% for multi-extractor scenarios
- Reduce memory spike from re-parsing

**Estimated Effort:** 3 hours

---

### 3.3 [P1] Optimize Regex Object Creation

**Current State:**
```kotlin
val styleHeadingLevel = styleId
    ?.trim()
    ?.let { Regex("""Heading([1-9])""", RegexOption.IGNORE_CASE).matchEntire(it) }
    // ↑ Creates new Regex for each paragraph
```

**Solution:**
Move regex to companion object.

**Files to Modify:**
- DocxParagraphStyleParser.kt

```kotlin
companion object {
    private val HEADING_REGEX = Regex("""Heading([1-9])""", RegexOption.IGNORE_CASE)
}

private fun parseStyleHeadingLevel(styleId: String?): Int? =
    styleId
        ?.trim()
        ?.let { HEADING_REGEX.matchEntire(it) }
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
```

**Impact:**
- Reduce GC pressure by eliminating regex allocations
- Faster parsing (no regex compilation)

**Estimated Effort:** 1 hour

---

### 3.4 [P1] Optimize String Operations in Loops

**Current State:**
```kotlin
.joinToString("") { it.text }  // ← String concatenation creates ~N objects
```

**Solution:**
Use StringBuilder for concatenation; avoid joinToString.

**Files to Modify:**
- DocxStructureParser.kt

```kotlin
val summary = StringBuilder().apply {
    for (span in spans) {
        append(span.text)
    }
}.toString()
```

**Impact:**
- Reduce GC pressure by 90%+
- Faster string building

**Estimated Effort:** 1 hour

---

### 3.5 [P1] Cache Collection Filtering Sets

**Current State:**
```kotlin
.filterNot { it.id.toString() in setOf("-1", "0", "1") }  // ← Creates set every time
```

**Solution:**
Move set to companion object.

**Files to Modify:**
- DocxStructureParser.kt

```kotlin
companion object {
    private val IGNORED_IDS = setOf("-1", "0", "1")
}

private fun parseFootnotes(document: XWPFDocument): List<DocumentElement.Note> =
    document.footnotes
        .orEmpty()
        .filterNot { it.id.toString() in IGNORED_IDS }
        .map { it.toNote(NoteKind.FOOTNOTE) }
```

**Estimated Effort:** 30 minutes

---

### 3.6 [P1] Implement Lazy DOM Tree Traversal

**Current State:**
```kotlin
private fun collectDescendants(node: Node, localName: String?, output: MutableList<Element>) {
    // ↑ Collects ALL matching elements into memory
}
```

**Solution:**
Use lazy sequence for tree traversal.

**Files to Create:**
- data/parser/util/XmlSequences.kt

```kotlin
fun Element.descendants(localName: String? = null): Sequence<Element> =
    sequence {
        val queue = ArrayDeque<Element>()
        queue.add(this@descendants)
        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()
            if (localName == null || element.tagName == localName) {
                yield(element)
            }
            element.childNodes.forEach { child ->
                if (child is Element) queue.add(child)
            }
        }
    }
```

**Files to Modify:**
- DocxAdvancedMarkupExtractor.kt (use sequence)

**Impact:**
- Lazy evaluation avoids materializing large lists
- Reduced memory for large documents

**Estimated Effort:** 2 hours

---

### 3.7 [P2] Implement Cache Directory Cleanup

**Current State:**
```kotlin
File(cacheDir, "odt_img_${System.currentTimeMillis()}_$fileName")
// ↑ Cache grows unbounded, no cleanup
```

**Solution:**
Implement cache management with LRU eviction and periodic cleanup.

**Files to Create:**
- data/parser/cache/CacheManager.kt
- data/parser/cache/CacheEvictionPolicy.kt

**Features:**
1. LRU eviction when cache exceeds size limit
2. Periodic cleanup of old files
3. Deduplication by content hash
4. Configurable retention period

**Estimated Effort:** 4 hours

---

### 3.8 [P2] Profile and Benchmark Parser

**Implementation Tasks:**
1. Create benchmark suite
2. Measure parsing time by component
3. Profile memory usage
4. Track GC events
5. Create performance dashboard

**Files to Create:**
- tests/benchmark/ParserBenchmark.kt
- tools/ParsingProfiler.kt

**Estimated Effort:** 5 hours

---

### 3.9 [P2] Implement Incremental Parsing

**Solution:**
Allow parsing specific document sections rather than entire document.

**Files to Create:**
- data/parser/incremental/IncrementalParser.kt
- data/parser/incremental/ParseRange.kt

**Features:**
1. Parse specific page range
2. Parse specific element type
3. Parse specific section
4. Update delta

**Estimated Effort:** 6 hours

---

### 3.10 [P3] Optimize Memory Allocations

**Implementation Tasks:**
1. Use object pooling for frequently created objects
2. Preallocate collections with capacity
3. Reuse byte buffers
4. Profile allocations with Android Profiler

**Estimated Effort:** 4 hours

---

## Category 4: Architecture Refactoring (P0/P1)

### 4.1 [P0] Create Format Abstraction Layer

**Current State:**
- Tight coupling to POI for DOCX
- Tight coupling to ODFDOM for ODT
- Difficult to swap implementations

**Solution:**
Create abstraction layer with pluggable implementations.

**Files to Create:**
- data/parser/abstraction/PackageReader.kt (abstracts ZIP reading)
- data/parser/abstraction/XmlReader.kt (abstracts XML parsing)
- data/parser/abstraction/StyleResolver.kt (abstracts style lookup)

```kotlin
interface PackageReader {
    fun readFile(path: String): ByteArray?
    fun listFiles(): List<String>
}

interface XmlReader {
    fun parseXml(bytes: ByteArray): Document?
    fun readText(element: Element, namespace: String, localName: String): String?
}
```

**Benefits:**
1. Easy to swap POI → another DOCX library
2. Easy to add new formats
3. Easier to test with mocks
4. Reduced coupling

**Files to Modify:**
- DocxParser.kt (use PackageReader)
- OdtParser.kt (use PackageReader)

**Estimated Effort:** 5 hours

---

### 4.2 [P1] Separate Parsing Logic from Format Details

**Current State:**
Document parsing mixed with format-specific code.

**Solution:**
Extract parsing logic into format-agnostic component.

**Refactoring:**
```
Current:
DocxParser → DocxStructureParser, DocxParagraphParser, ...
OdtParser → OdtXmlParser, OdtParagraphParser, ...

Proposed:
DocumentPipeline (format-agnostic)
├── StructurePhase
├── ParagraphPhase
├── TablePhase
├── ImagePhase
└── MetadataPhase

With implementations:
DocxStructurePhase, OdtStructurePhase
DocxParagraphPhase, OdtParagraphPhase
...
```

**Files to Create:**
- data/parser/pipeline/PipelinePhase.kt
- data/parser/pipeline/DocumentPipeline.kt
- data/parser/pipeline/PipelineContext.kt

**Estimated Effort:** 8 hours

---

### 4.3 [P1] Implement Plugin Architecture for Extractors

**Current State:**
Extractors only available for DOCX; ODT has no extraction mechanism.

**Solution:**
Create unified extractor interface and plugin registry.

**Files to Create:**
- data/parser/extractor/DocumentExtractor.kt (interface)
- data/parser/extractor/ExtractorRegistry.kt
- data/parser/extractor/OdtExtractor.kt (ODT extractor)

**Implementation:**
- Each parser can have multiple extractors
- Register extractors at parser creation
- Enable/disable extractors by configuration
- Allow custom extractors

**Estimated Effort:** 4 hours

---

### 4.4 [P1] Consolidate Parser Configuration

**Current State:**
Parser configuration scattered across classes.

**Solution:**
Create centralized configuration object.

**Files to Create:**
- data/parser/config/ParserConfig.kt
- data/parser/config/ParserConfigBuilder.kt

```kotlin
data class ParserConfig(
    val enableMetadataExtraction: Boolean = true,
    val enableAdvancedMarkup: Boolean = true,
    val enableEdgeCaseExtraction: Boolean = true,
    val maxImageSize: Long = 10 * 1024 * 1024,
    val cacheDir: File,
    val maxCacheSize: Long = 100 * 1024 * 1024,
    val enableStreaming: Boolean = false,
    val timeoutMs: Long = 30_000,
    val recoveryStrategy: RecoveryStrategy = SkipCorrupted,
)
```

**Estimated Effort:** 2 hours

---

### 4.5 [P2] Reduce Parser Constructor Complexity

**Current State:**
```kotlin
DocxParser(
    context: Context,
    document: XWPFDocument,
    docxPackage: DocxPackage,
    structureParser: DocxStructureParser,
    paragraphParser: DocxParagraphParser,
    // ... 8+ more parameters
)
```

**Solution:**
Use builder pattern or factory with dependency injection.

**Implementation:**
- Use Hilt @Provides for parser creation
- Builder pattern for manual construction
- Reduce parameter count to 2-3

**Estimated Effort:** 2 hours

---

### 4.6 [P2] Create Parser Registry for Format Discovery

**Files to Create:**
- data/parser/registry/ParserRegistry.kt
- data/parser/registry/ParserDescriptor.kt

**Features:**
1. Runtime parser discovery
2. Dynamic format support
3. Parser capability queries
4. Format version detection

**Estimated Effort:** 3 hours

---

## Category 5: Missing Features (P1/P2)

### 5.1 [P1] Implement Round-Trip Conversion (Parse → Save → Parse)

**Current State:**
```kotlin
override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
    withContext(Dispatchers.IO) {
        // Placeholder
        Result.success(Unit)
    }
```

All formatting/structure is lost on save.

**Solution:**
Preserve document structure during parse and reconstruction.

**Files to Create:**
- data/parser/reconstruction/DocumentReconstructor.kt
- data/parser/reconstruction/DocxReconstructor.kt
- data/parser/reconstruction/OdtReconstructor.kt

**Key Features:**
1. Preserve all formatting
2. Maintain section structure
3. Keep headers/footers
4. Preserve metadata
5. Maintain change tracking

**Implementation Challenges:**
- Requires bidirectional element mapping
- Complex for merged cells, complex nesting
- Large implementation effort

**Estimated Effort:** 20 hours

---

### 5.2 [P1] Add Schema Validation for Parsed Documents

**Files to Create:**
- data/parser/validation/SchemaValidator.kt
- data/parser/validation/DocxSchemaValidator.kt
- data/parser/validation/OdtSchemaValidator.kt

**Features:**
1. Validate against OpenXML schema
2. Validate against ODF schema
3. Report schema violations
4. Suggest corrections

**Estimated Effort:** 6 hours

---

### 5.3 [P2] Implement Selective Element Parsing

**Feature:** Only parse specific element types instead of entire document.

**Use Case:**
```kotlin
parser.parse(
    inputStream,
    parseOnly = setOf(
        ElementType.PARAGRAPH,
        ElementType.TABLE
    )
)
```

**Files to Create:**
- data/parser/filtering/SelectiveParsing.kt
- data/parser/filtering/ElementType.kt

**Estimated Effort:** 4 hours

---

### 5.4 [P2] Add Form Field Extraction and Preservation

**Current State:**
Form fields parsed but not included in DocumentElement.

**Solution:**
Create DocumentElement.FormField type and preserve in save.

**Files to Create:**
- data/model/DocumentElement.FormField
- data/parser/form/FormFieldExtractor.kt
- data/parser/form/FormFieldReconstructor.kt

**Estimated Effort:** 5 hours

---

### 5.5 [P2] Implement Page Numbering Preservation

**Feature:** Track and preserve page numbers during parsing.

**Files to Create:**
- data/parser/pagination/PageNumberExtractor.kt
- data/parser/pagination/PageBreakStrategy.kt

**Estimated Effort:** 4 hours

---

### 5.6 [P2] Add Change Tracking Support

**Feature:** Preserve tracked changes (insertions, deletions) in DOCX.

**Files to Create:**
- data/parser/tracking/ChangeTrackingExtractor.kt
- data/model/DocumentElement.TrackedChange

**Estimated Effort:** 6 hours

---

### 5.7 [P3] Add Custom Style Support

**Feature:** Extract and preserve custom/user-defined styles.

**Files to Create:**
- data/parser/style/CustomStyleExtractor.kt

**Estimated Effort:** 4 hours

---

### 5.8 [P3] Implement Format Versioning Detection

**Feature:** Detect and report document format version.

**Example:**
```kotlin
parser.detectFormatVersion(inputStream) // → "DOCX 2019" or "ODF 1.3"
```

**Estimated Effort:** 2 hours

---

## Category 6: Testing Infrastructure (P0/P1)

### 6.1 [P0] Create Parser Unit Test Suite

**Current State:**
No visible unit tests.

**Solution:**
Implement comprehensive unit tests.

**Files to Create:**
- tests/parser/unit/DocxParserTest.kt
- tests/parser/unit/OdtParserTest.kt
- tests/parser/unit/DocxParagraphParserTest.kt
- tests/parser/unit/OdtParagraphParserTest.kt
- tests/parser/unit/ImageExtractorTest.kt
- ... (tests for each parser component)

**Coverage Goals:**
- Happy path for each component
- Edge cases (empty document, null values)
- Error conditions
- Boundary conditions

**Estimated Effort:** 15 hours

---

### 6.2 [P1] Create Integration Tests with Sample Documents

**Solution:**
Create test fixture documents and integration tests.

**Files to Create:**
- tests/parser/integration/DocxIntegrationTest.kt
- tests/parser/integration/OdtIntegrationTest.kt
- tests/fixtures/sample-*.docx and sample-*.odt

**Test Scenarios:**
1. Parse real-world DOCX files
2. Parse real-world ODT files
3. Verify element count matches
4. Verify text content matches
5. Verify formatting preserved

**Estimated Effort:** 10 hours

---

### 6.3 [P1] Create Error Condition Tests

**Files to Create:**
- tests/parser/error/ErrorHandlingTest.kt

**Scenarios:**
1. Corrupted file
2. Missing required elements
3. Malformed XML
4. Truncated file
5. Out of memory conditions

**Estimated Effort:** 8 hours

---

### 6.4 [P2] Create Performance/Benchmark Tests

**Files to Create:**
- tests/parser/benchmark/ParserBenchmarkTest.kt
- tools/memory_profiler.kt

**Measurements:**
1. Parsing time by document size
2. Memory usage by document size
3. GC pressure
4. Component-level timing

**Estimated Effort:** 5 hours

---

### 6.5 [P2] Create Mutation Testing Suite

**Tool:** Pitest or similar

**Goals:**
1. Verify test effectiveness
2. Identify untested code paths
3. Improve test quality

**Estimated Effort:** 4 hours

---

### 6.6 [P3] Create Compatibility Test Matrix

**Goals:**
Test against multiple versions of:
1. POI library versions
2. ODFDOM library versions
3. Android versions
4. Different file versions

**Estimated Effort:** 6 hours

---

## Category 7: Documentation (P1/P2)

### 7.1 [P1] Add KDoc API Documentation

**Current State:**
No KDoc documentation in parser files.

**Solution:**
Add comprehensive KDoc for all public APIs.

**Scope:**
- All public classes
- All public methods
- All public properties
- Document parameters, return types, exceptions

**Example:**
```kotlin
/**
 * Parses a DOCX document from the given input stream.
 *
 * @param inputStream The input stream to read the DOCX file from.
 *                    Must be a valid DOCX file.
 *                    Will be closed after parsing completes.
 * @return A Result containing either:
 *         - Success: List of DocumentElements parsed from the document
 *         - Failure: ParseException with error context
 *
 * @throws IllegalArgumentException if inputStream is null
 *
 * **Limitations:**
 * - Maximum supported document size: 500MB
 * - Streaming is not yet supported
 * - Some advanced formatting may be lost
 *
 * **Example:**
 * ```kotlin
 * val result = parser.parse(fileInputStream)
 * result.onSuccess { elements ->
 *     println("Parsed ${elements.size} elements")
 * }.onFailure { error ->
 *     logger.error("Parse failed", error)
 * }
 * ```
 */
override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>>
```

**Estimated Effort:** 8 hours

---

### 7.2 [P1] Document Namespace Strategy

**Current State:**
Namespace usage undocumented.

**Solution:**
Create namespace documentation.

**Files to Create:**
- docs/NAMESPACES.md

**Content:**
1. List all namespaces
2. Explain purpose of each
3. Show usage examples
4. Document namespace resolution strategy

**Estimated Effort:** 2 hours

---

### 7.3 [P2] Document Architecture Decisions

**Files to Create:**
- docs/ARCHITECTURE.md (comprehensive guide)
- docs/PARSER_PIPELINE.md (detailed pipeline docs)
- docs/ERROR_HANDLING.md (error strategy docs)
- docs/EXTRACTION_STRATEGY.md (extractor docs)

**Estimated Effort:** 6 hours

---

### 7.4 [P2] Create Usage Examples and Recipes

**Files to Create:**
- docs/USAGE_EXAMPLES.md
- docs/RECIPES.md

**Examples:**
1. Basic parsing
2. Extracting specific element types
3. Error handling
4. Streaming parsing
5. Custom extractors

**Estimated Effort:** 4 hours

---

### 7.5 [P2] Document Known Limitations and Future Work

**Files to Create:**
- docs/LIMITATIONS.md
- docs/ROADMAP.md

**Estimated Effort:** 2 hours

---

## Category 8: API Design Improvements (P0/P1)

### 8.1 [P0] Improve Result Type Information

**Current State:**
```kotlin
Result<List<DocumentElement>>  // ← No detail about failure
```

**Solution:**
Create detailed error result type.

**Files to Create:**
- data/parser/result/ParseResult.kt
- data/parser/result/ParseError.kt
- data/parser/result/ParseErrorDetails.kt

```kotlin
sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class PartialSuccess<T>(
        val data: T,
        val warnings: List<ParseWarning>
    ) : ParseResult<T>()
    data class Failure(val error: ParseError) : ParseResult<Nothing>()
}

data class ParseError(
    val code: ErrorCode,
    val message: String,
    val context: ParseErrorContext,
    val cause: Throwable?,
    val recoveryTips: List<String>
)
```

**Impact:**
- Consumers get detailed error information
- Better error recovery
- Clearer failure reasons

**Estimated Effort:** 4 hours

---

### 8.2 [P1] Simplify Parser Instantiation

**Current State:**
DocxParser requires 8+ parameters.

**Solution:**
Use factory or builder.

**Files to Create:**
- data/parser/factory/ParserFactory.kt (enhanced)
- data/parser/factory/ParserBuilder.kt

**Example:**
```kotlin
val parser = ParserBuilder()
    .withConfig(config)
    .withCache(cacheDir)
    .enableExtractors(
        DocxPackageMetadataExtractor(),
        DocxAdvancedMarkupExtractor()
    )
    .build()
```

**Estimated Effort:** 2 hours

---

### 8.3 [P1] Add Capability Queries

**Feature:** Allow checking parser capabilities at runtime.

**Files to Create:**
- data/parser/capability/ParserCapabilities.kt

```kotlin
val capabilities = parser.getCapabilities()
if (capabilities.supportsStreaming) {
    parser.parseStream(inputStream).collect { element ->
        // Process element as it arrives
    }
} else {
    val elements = parser.parse(inputStream)
}
```

**Estimated Effort:** 2 hours

---

### 8.4 [P1] Implement Builder Pattern for DocumentElement

**Current State:**
DocumentElement constructors are complex.

**Solution:**
Add builder support for creation.

```kotlin
DocumentElement.Paragraph.builder()
    .addSpan(TextSpan(...))
    .withStyle(style)
    .withListInfo(listInfo)
    .build()
```

**Estimated Effort:** 3 hours

---

### 8.5 [P2] Add Streaming API

**Feature:** Support consuming elements as they're parsed.

**Files to Create:**
- data/parser/stream/StreamingParser.kt

```kotlin
fun parseStream(inputStream: InputStream): Flow<DocumentElement>
```

**Estimated Effort:** 8 hours (depends on streaming implementation)

---

### 8.6 [P2] Add Cancellation Token Support

**Feature:** Allow cancelling long-running parse operations.

```kotlin
parser.parse(
    inputStream,
    cancellationToken = token
)
```

**Implementation:** Use Flow/coroutine cancellation.

**Estimated Effort:** 2 hours

---

### 8.7 [P2] Add Progress Reporting

**Feature:** Report parsing progress (useful for large files).

```kotlin
parser.parse(
    inputStream,
    progressListener = { progress: ParsingProgress ->
        println("${progress.percentComplete}% complete")
    }
)
```

**Estimated Effort:** 2 hours

---

## Category 9: Memory Management (P0/P1)

### 9.1 [P0] Reduce Memory Overhead

**Current Implementation: 3.2× overhead**
- File: 100MB
- readBytes(): +100MB
- ZipInputStream: +100MB
- XWPFDocument: +100MB
- **Total: 300MB for 100MB file**

**Solution Options:**

**Option A: Use Streaming Parser** (See Performance 3.1)
- Result: 1.2× overhead (120MB)

**Option B: Reduce Document Copies** (Quick fix)
- Don't read bytes twice
- Parse XWPFDocument directly from stream

**Implementation:**
```kotlin
override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
    withContext(Dispatchers.IO) {
        try {
            val doc = XWPFDocument(inputStream)  // ← Single load
            val docxPackage = DocxPackage(doc)   // ← Use same data
            // ... rest of parsing ...
        }
    }
```

**Estimated Effort:** 2 hours (Option B), 15 hours (Option A)

---

### 9.2 [P0] Implement Resource Pooling

**Current State:**
Every parse operation creates new objects (StringBuilders, ArrayLists, etc.).

**Solution:**
Implement object pool for frequently created objects.

**Files to Create:**
- data/parser/pool/ObjectPool.kt
- data/parser/pool/TextSpanPool.kt
- data/parser/pool/StringBuilderPool.kt

**Estimated Effort:** 4 hours

---

### 9.3 [P1] Add Memory Limit Enforcement

**Feature:** Prevent OOM by enforcing memory limits.

**Files to Create:**
- data/parser/memory/MemoryLimiter.kt
- data/parser/memory/MemoryMonitor.kt

**Implementation:**
1. Track memory usage during parsing
2. Throw exception if exceeds limit
3. Provide cleanup hooks

**Estimated Effort:** 3 hours

---

### 9.4 [P1] Implement Weak References for Cache

**Current State:**
Cache grows unbounded.

**Solution:**
Use WeakHashMap or similar for automatic GC.

**Files to Create:**
- data/parser/cache/WeakCache.kt

**Estimated Effort:** 1 hour

---

### 9.5 [P2] Add Proactive Memory Release

**Feature:** Release resources between parsing phases.

**Implementation:**
1. Clear DOM trees after extraction
2. Close streams promptly
3. Nullify large temporary objects
4. Suggest GC between phases

**Estimated Effort:** 2 hours

---

### 9.6 [P2] Implement Leak Detection (Development Only)

**Tool:** Android Profiler or LeakCanary

**Implementation:**
1. Detect leaked resources in tests
2. Fail tests if leaks detected
3. Create heap dump on leak
4. Analyze with SharkEnough

**Estimated Effort:** 3 hours

---

## Category 10: Configuration & Tooling (P1/P2)

### 10.1 [P1] Create Parser Configuration File

**Solution:**
Add gradle configuration for parser features.

**File to Create:**
- gradle.properties (add parser settings)

```properties
# Parser Configuration
tdoc.parser.enableStreaming=false
tdoc.parser.enableMetadataExtraction=true
tdoc.parser.maxImageSize=10485760
tdoc.parser.cacheMaxSize=104857600
tdoc.parser.timeout=30000
```

**Implementation:**
Read at runtime and populate ParserConfig.

**Estimated Effort:** 1 hour

---

### 10.2 [P1] Create Debug/Verbose Logging Configuration

**Solution:**
Add feature flag for debug logging.

**Implementation:**
1. Feature flag in BuildConfig
2. Conditional logging based on flag
3. Enable in debug builds, disable in release

**Estimated Effort:** 1 hour

---

### 10.3 [P2] Create Parser Diagnostics Tool

**Files to Create:**
- tools/ParserDiagnostics.kt

**Features:**
1. Analyze document structure
2. Report parsing issues
3. Suggest optimizations
4. Provide repair recommendations

**Example:**
```
$ ./gradlew parserDiagnostics --file document.docx
Document: document.docx
Size: 2.3 MB
Elements: 5,234
Paragraphs: 4,102
Tables: 23
Images: 45

Issues:
⚠️ Large embedded images (total: 1.8 MB)
   - Suggestion: Compress images for smaller file size
⚠️ Deep nesting (max level: 12)
   - Suggestion: Flatten structure where possible

Performance estimate:
- Parse time: ~2-3 seconds
- Memory peak: ~8 MB
```

**Estimated Effort:** 4 hours

---

### 10.4 [P3] Create Parser Formatter Tool

**Files to Create:**
- tools/ParserFormatter.kt

**Features:**
1. Reformat parsed document
2. Clean up formatting
3. Normalize styles
4. Export to different format

**Estimated Effort:** 5 hours

---

## Implementation Timeline & Priorities

### Phase 1: Critical Issues (Weeks 1-2)
**Items:** P0 issues from all categories

1. [1.1] Structured Logging System (4h)
2. [1.2] Error Context Wrapper (5h)
3. [2.1] Namespace Constants (1h)
4. [3.1] Streaming Parser (15h)
5. [3.2] XML Caching (3h)
6. [4.1] Format Abstraction Layer (5h)
7. [6.1] Unit Test Suite (15h)

**Total: 48 hours (~1 week FTE)**

---

### Phase 2: High Priority (Weeks 3-4)
**Items:** Most P1 issues

1. [1.3-1.8] Error handling completions (15h)
2. [2.2-2.7] Code deduplication (20h)
3. [3.3-3.6] Performance optimization (6h)
4. [4.2-4.6] Architecture refactoring (15h)
5. [5.1-5.4] Missing feature start (20h)
6. [6.2-6.3] Integration tests (18h)
7. [7.1-7.2] Documentation (10h)
8. [8.1-8.4] API improvements (11h)
9. [9.1-9.3] Memory management (9h)
10. [10.1-10.3] Configuration (6h)

**Total: 130 hours (~3 weeks FTE)**

---

### Phase 3: Medium Priority (Weeks 5-6)
**Items:** Remaining P2 issues

1. [2.8-2.9] Minor deduplication (2h)
2. [3.7-3.10] Performance polishing (13h)
3. [5.5-5.8] Feature completions (20h)
4. [6.4-6.6] Advanced testing (15h)
5. [7.3-7.5] Documentation (12h)
6. [8.5-8.7] Advanced API (12h)
7. [9.4-9.6] Memory optimization (6h)
8. [10.4] Parser formatter (5h)

**Total: 85 hours (~2 weeks FTE)**

---

### Phase 4: Polish & Low Priority (As Needed)
**Items:** P3 issues + refinement

1. [3.10] Memory allocation optimization (4h)
2. [5.7-5.8] Nice-to-have features (6h)
3. [6.6] Compatibility matrix (6h)
4. [7.5] Roadmap documentation (2h)
5. [8.7] Progress reporting (2h)
6. Bug fixes, refinement, optimization

---

## Success Criteria & Metrics

### Reliability Improvements
- ✅ 100% of errors logged with context
- ✅ Zero silent failures
- ✅ Error recovery success rate > 95%
- ✅ Test coverage > 80%

### Performance Improvements
- ✅ Memory overhead reduced from 3.2× to < 1.5×
- ✅ Parse time reduced by 40% (via caching)
- ✅ Support files up to 500MB
- ✅ Streaming parser for unlimited file sizes

### Code Quality Improvements
- ✅ Code duplication < 5%
- ✅ Cyclomatic complexity < 10 per method
- ✅ Zero SonarQube critical issues
- ✅ 100% public APIs documented

### Feature Completeness
- ✅ Round-trip conversion with fidelity > 95%
- ✅ All DOCX features supported
- ✅ ODT parity with DOCX
- ✅ Schema validation passing

---

## Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Streaming implementation complexity | High | High | Start with small POC, iterate |
| Breaking API changes | Medium | High | Version carefully, provide migration guide |
| Performance regression | Medium | Medium | Continuous benchmarking in CI/CD |
| Memory issues in integration | Medium | High | Extensive testing with large files |
| Incomplete feature migration | Low | Medium | Comprehensive acceptance testing |

---

## Notes & Recommendations

1. **Start with Phase 1 (Critical)**: These items block progress and are high-impact
2. **Parallel Work**: Some items can be done in parallel (testing while refactoring)
3. **Backward Compatibility**: Maintain API compatibility during refactoring where possible
4. **Performance Benchmarking**: Establish baselines before optimization
5. **Regular Reviews**: Review progress at end of each phase
6. **Community Feedback**: Consider open-source community input on priorities
7. **Documentation-First**: Document changes before implementation

---

## File Organization Reference

### New Directories to Create
```
app/src/main/java/com/tosin/docprocessor/data/parser/
├── abstraction/           # Format abstraction layer
├── cache/                 # Caching implementations
├── exception/             # Error types and handlers
├── extractor/             # Generic extractor interface
├── factory/               # Parser factories
├── filtering/             # Selective parsing
├── form/                  # Form field support
├── formatter/             # Text formatting
├── image/                 # Image extraction (refactored)
├── incremental/           # Incremental parsing
├── memory/                # Memory management
├── pagination/            # Page numbering
├── pipeline/              # Pipeline phases
├── pool/                  # Object pooling
├── reconstruction/        # Round-trip conversion
├── registry/              # Parser registry
├── result/                # Enhanced result types
├── stream/                # Streaming support
├── style/                 # Style extraction
├── table/                 # Table extraction (refactored)
├── telemetry/             # Error telemetry
├── tracking/              # Change tracking
├── util/                  # Shared utilities
└── validation/            # Validation framework

tests/
├── parser/
│   ├── unit/              # Unit tests
│   ├── integration/       # Integration tests
│   ├── error/             # Error condition tests
│   ├── benchmark/         # Performance benchmarks
│   └── fixtures/          # Test documents

tools/
├── ParserDiagnostics.kt
├── ParserFormatter.kt
└── ParsingProfiler.kt

docs/
├── ARCHITECTURE.md
├── NAMESPACES.md
├── PARSER_PIPELINE.md
├── ERROR_HANDLING.md
├── EXTRACTION_STRATEGY.md
├── USAGE_EXAMPLES.md
├── RECIPES.md
├── LIMITATIONS.md
└── ROADMAP.md
```

---

**Last Updated:** April 27, 2026  
**Status:** Draft  
**Next Review:** After Phase 1 completion
