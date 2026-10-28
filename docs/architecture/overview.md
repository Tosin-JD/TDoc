# Architecture

TDoc's architecture follows a **layered, unidirectional data flow** pattern. The three-tier design separates:

1. **How documents are stored** — the immutable block list model
2. **How they are measured** — the background pagination engine
3. **How they are drawn** — the custom canvas render pass

---

## Module Graph

```
┌─────────────────────────────────────────────────────────────┐
│  Presentation Layer          (Android / Compose)             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  UI Layer    │◄──►│ EditorViewModel│◄──►│   Events     │  │
│  │  (Composables)│    │ (UDF State)  │    │ (Events)     │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
           ▲                      ▲                      ▲
           │                      │                      │
┌───────────┴───────────┐ ┌───────────┴───────────┐ ┌───────────┴───────────┐
│  Domain Layer         │ │  Data Layer           │ │  Rendering Engine     │
│  (Models / Rules)     │ │  (Repository / Parser)│ │  (Canvas Render)      │
│  ┌──────────────┐     │ │  ┌──────────────┐     │ │  ┌──────────────┐     │
│  │ DocumentData │     │ │  │ Repository │     │ │  │ PrintPaginator│    │
│  │ DocumentElement│   │ │  │ (IO)       │     │ │  │ IncrementalPaginator│ │
│  │ Block Model  │     │ │  │            │     │ │  │ LayoutRegistry    │    │
│  └──────────────┘     │ │  └──────────────┘     │ │  └──────────────┘     │
└───────────────────────┘ └───────────────────────┘ └───────────────────────┘
```

---

## Layer Responsibilities

| Layer | Responsibility | Key Types |
|---|---|---|
| **Presentation** | UI, user input, state flow | `EditorScreen`, `EditorViewModel`, `PrintLayout`, `PageView` |
| **Domain** | Business rules, pagination, measurement | `DocumentElement`, `Paginator`, `TextMeasurer`, `LayoutRegistry` |
| **Data** | File IO, parsing, persistence | `DocumentRepository`, `ParserFactory`, `OdtParser`/`DocxParser` |
| **Rendering** | Low-level drawing, canvas | `PrintElementRenderer`, `Canvas` draw pass |

---

## Data Flow (Edit Cycle)

```
User types key
  │
  ▼
EditorScreen (Compose) — receives keyboard/selection events
  │
  ▼
EditorViewModel.onParagraphEdited(index, newContent)  ← event
  │
  ▼
ViewModel:
  1. Mutates documentElements[index] (immutable copy)
  2. Emits editIndex to signal a pending change
  3. Computes new state
  │
  ▼
PrintLayout (Composable) collects new state + editIndex
  │
  ▼
LaunchedEffect triggers pagination:
  ├─ If editIndex != null → IncrementalPaginator.incrementalUpdate()
  └─ Else → PrintPaginator.paginate()
  │
  ▼
Produces new List<PageModel>
  │
  ▼
Builds stable keys + document fingerprint
  │
  ▼
Update pagesState → recompose LazyColumn
  │
  ▼
PageView renders via Canvas draw pass
```

---

## State Shapes

### EditorUiState

```kotlin
data class EditorUiState(
    val documentData: DocumentData? = null,
    val viewMode: ViewMode = ViewMode.MOBILE,
    val editorMode: EditorMode = EditorMode.PREVIEW,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDarkMode: Boolean = false,
    val pageLayout: PageLayoutState = PageLayoutState(),
    // ... other UI concerns
)
```

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

### PageModel

```kotlin
data class PageModel(
    val index: Int,
    val dimensions: PageDimensions,
    val elements: List<PositionedElement>,
    val state: PageState = PageState.CLEAN,
    val stableKey: String = "",
    val documentFingerprint: Long = 0L
)
```

---

## Cross-Cutting Concerns

* **Error handling:** Domain errors are converted to `ParseDiagnostic` and surfaced via `EditorUiState.diagnostics`. UI shows them in a `DiagnosticsSummaryChip`.
* **Cancellation:** Background pagination runs in `viewModelScope.launch(Dispatchers.Default)`. Long-running jobs are not explicitly cancelled yet — see TODOs.
* **Testing:** Domain logic (pagination, measurement) is unit-tested. UI tests use Android instrumentation tests (`androidTest`).
