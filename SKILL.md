---
name: kotlin-docx-parser
description: >
  Complete guide for building a production-grade .docx parser in Kotlin using Apache POI and
  Jetpack Compose. Use this skill whenever the user wants to parse, extract, render, or process
  Word documents (.docx files) in an Android or Kotlin project — even if they just say "read a Word
  file", "extract text from docx", "display a document in Compose", "build a document editor",
  or "handle .docx in my app". Also triggers for questions about integrating an AI/LLM agent into
  document parsing, mapping extracted content to a Room database, or converting document layout
  from print to mobile view. When in doubt about whether this skill applies, use it.
---

# Kotlin .docx Parser Skill

This skill guides you through building a complete, AI-augmented .docx parser for Android using
Apache POI, Kotlin Coroutines, Jetpack Compose, and the Anthropic API. It covers the full pipeline
from raw file ingestion to rendering structured document content in a mobile UI.

Read the relevant reference file before writing code:
- **`references/poi-extraction.md`** — Apache POI API patterns, section tree building, table conversion
- **`references/agent-skills.md`** — LLM agent skill implementations (CoT extraction, windowing, schema mapping)
- **`references/compose-rendering.md`** — Jetpack Compose document viewer components and layout inference

---

## Architecture overview

The parser is split into four layers. Build them in this order — each layer depends on the previous.

```
.docx file
    ↓
[ Layer 1: Ingestion ]      Apache POI — reads XML, extracts raw structure
    ↓
[ Layer 2: Agent skills ]   LLM pipeline — CoT extraction, windowing, schema mapping
    ↓
[ Layer 3: Data layer ]     Room DB — persists FileModel, sections, tables, images
    ↓
[ Layer 4: UI ]             Jetpack Compose — adaptive Mobile / Print layout renderer
```

---

## Quick-start: Gradle dependencies

Add these to your `build.gradle.kts` (app module). Use exact versions — POI has breaking API
changes across majors.

```kotlin
dependencies {
    // Apache POI — .docx support
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Required POI runtime deps (not pulled in transitively on Android)
    implementation("org.apache.xmlbeans:xmlbeans:5.2.1")
    implementation("com.github.virtuald:curvesapi:1.08")

    // Kotlin Serialization — JSON ↔ FileModel
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room — persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose (Bill of Materials keeps versions in sync)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
}
```

**Android-specific POI note:** POI uses `java.awt` classes that are absent on Android. Add this
to your `build.gradle.kts` (app module) to prevent crashes:

```kotlin
android {
    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}
```

---

## Core data model

Define this before writing any parser or UI code. Everything else maps to or from it.

```kotlin
@Serializable
@Entity(tableName = "files")
data class FileModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val title: String?,
    val date: String?,           // ISO-8601 if found, else null
    val parties: List<String>,   // Names/signatories extracted by agent
    val summary: String?,
    val rawMarkdown: String,     // Full doc as Markdown (tables + headings preserved)
    val layoutHint: String,      // "LazyColumn" | "Row" — from layout inference skill
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sections", foreignKeys = [ForeignKey(...)])
data class DocSection(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fileId: String,
    val level: Int,              // 1–6 matching Heading level; 0 = body
    val title: String,
    val content: String,
    val orderIndex: Int
)

@Entity(tableName = "images")
data class DocImage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fileId: String,
    val sectionId: String?,
    val data: ByteArray,
    val mimeType: String,
    val aiDescription: String?   // Filled by vision agent skill
)
```

---

## Pipeline: step-by-step

### Step 1 — Ingest with Apache POI

See **`references/poi-extraction.md`** for the full API reference and edge cases.

Key entry point:

```kotlin
class DocxParser(private val context: Context) {
    fun open(uri: Uri): XWPFDocument {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open $uri")
        return XWPFDocument(stream)
    }
}
```

### Step 2 — Build section tree

Walk `doc.paragraphs`, check `para.style` for Heading1–Heading6, and construct a
`List<DocSection>` hierarchy. This index powers the **windowing** agent skill — the LLM
requests individual sections rather than the full document.

```kotlin
fun buildSectionIndex(doc: XWPFDocument): Map<String, String> {
    val index = mutableMapOf<String, StringBuilder>()
    var currentKey = "preamble"
    index[currentKey] = StringBuilder()

    doc.paragraphs.forEach { para ->
        val level = headingLevel(para.style) // 0 = body, 1–6 = heading
        if (level > 0) {
            currentKey = para.text.trim()
            index[currentKey] = StringBuilder()
        } else {
            index[currentKey]?.append(para.text)?.append("\n")
        }
    }
    return index.mapValues { it.value.toString() }
}

private fun headingLevel(style: String?): Int = when (style) {
    "Heading1" -> 1; "Heading2" -> 2; "Heading3" -> 3
    "Heading4" -> 4; "Heading5" -> 5; "Heading6" -> 6
    else -> 0
}
```

### Step 3 — Convert tables to Markdown

Sends Markdown to the LLM instead of raw text, preserving row/column relationships.
Never send a serialized XWPFTable object — the LLM cannot parse it.

```kotlin
fun tableToMarkdown(table: XWPFTable): String = buildString {
    table.rows.forEachIndexed { ri, row ->
        val cells = row.tableCells.joinToString(" | ") { cell ->
            cell.text.trim().replace("\n", " ")
        }
        appendLine("| $cells |")
        if (ri == 0) {
            val sep = row.tableCells.joinToString(" | ") { "---" }
            appendLine("| $sep |")
        }
    }
}
```

### Step 4 — Run agent skills

See **`references/agent-skills.md`** for full prompts and implementation of:
- **Windowing** — `get_section` MCP tool that returns one section at a time
- **Chain-of-thought extraction** — 3-step verified loop per field
- **Schema mapping** — JSON output deserialized to `FileModel`
- **Vision parsing** — base64 image sent to Claude for embedded charts/diagrams
- **Prompt caching** — section tree cached as ephemeral system block

### Step 5 — Infer layout

Reads section metadata from `doc.document.body` to decide between `LazyColumn` (single-column
mobile) and `Row` (multi-column print). See **`references/compose-rendering.md`**.

### Step 6 — Persist to Room

```kotlin
@Dao
interface FileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<DocSection>)

    @Query("SELECT * FROM files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FileModel>>
}
```

---

## ViewModel — orchestrating the pipeline

```kotlin
@HiltViewModel
class DocParserViewModel @Inject constructor(
    private val parser: DocxParser,
    private val agent: DocAgentClient,
    private val dao: FileDao
) : ViewModel() {

    sealed interface ParseState {
        data object Idle : ParseState
        data class Loading(val step: String) : ParseState
        data class Done(val model: FileModel) : ParseState
        data class Error(val message: String) : ParseState
    }

    val state = MutableStateFlow<ParseState>(ParseState.Idle)

    fun process(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                state.value = ParseState.Loading("Reading file…")
                val doc = parser.open(uri)

                state.value = ParseState.Loading("Building structure…")
                val sectionIndex = buildSectionIndex(doc)
                val tables = doc.tables.map(::tableToMarkdown)

                state.value = ParseState.Loading("Running AI extraction…")
                val fileModel = agent.extractAll(
                    sectionIndex = sectionIndex,
                    tables = tables,
                    images = doc.allPictures
                )

                state.value = ParseState.Loading("Saving…")
                dao.insertFile(fileModel)
                dao.insertSections(fileModel.toDocSections())

                state.value = ParseState.Done(fileModel)
            }.onFailure { e ->
                state.value = ParseState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

---

## Common pitfalls

| Problem | Cause | Fix |
|---|---|---|
| `NoClassDefFoundError: java.awt.Color` | POI uses AWT internals | Add `poi-ooxml-lite` or exclude AWT via packagingOptions |
| Paragraphs have no style | Doc used direct formatting, not styles | Fall back to font size: >16pt → treat as heading |
| Merged table cells cause column shift | POI iterates physical cells only | Track `gridSpan` attribute and pad missing cells |
| LLM hallucinates dates | Full doc sent at once | Use windowing — send only the section near a date-like keyword |
| Compose recompose on every scroll | `FileModel` not stable | Annotate with `@Stable` or use `ImmutableList` from `kotlinx.collections.immutable` |

---

## Reference files

Read these when implementing each layer:

- **`references/poi-extraction.md`** — Full Apache POI API, footnotes, images, headers/footers, tracked changes
- **`references/agent-skills.md`** — Anthropic API calls, prompt caching, CoT extraction prompts, MCP tool definitions, vision parsing
- **`references/compose-rendering.md`** — DocViewer composable, MarkdownTable, layout inference, Print vs Mobile toggle