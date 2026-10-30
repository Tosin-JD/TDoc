# Presentation Layer

This document describes the presentation layer of TDoc: how the UI is structured, how state flows from the ViewModel, and how the editor screen renders pages.

---

## Architecture Pattern: UDF (Unidirectional Data Flow)

TDoc uses a **UDF** pattern rather than classic MVVM or MVI:

1. **State is immutable** and held in `EditorViewModel`
2. **Events** (`onParagraphEdited`, `onSectionHeaderEdited`, etc.) mutate state
3. **UI collects `StateFlow`** and recomposes when state changes
4. **Side effects** (pagination, file I/O) run in `viewModelScope` and emit events

This is a lighter variant of MVI — there is no separate "intent" type; events are methods on the ViewModel.

---

## Key Components

### EditorScreen

The root composable for the editor. It:

- Collects `uiState` from `EditorViewModel`
- Collects `documentData` for the print layout
- Delegates to `PrintLayout` or `MobileLayout` based on `viewMode`
- Wraps `LazyColumn` with `listState` for virtualized scrolling

```kotlin
@Composable
fun EditorScreen(
    openDocumentUri: Uri? = null,
    onCloseRequest: () -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val documentData by viewModel.currentDocument.collectAsState()
    // ... Scaffold, TopAppBar, Column with lazy list
}
```

### EditorViewModel

The single source of truth for editor state. Responsibilities:

* Holds `documentElements` (immutable `List<DocumentElement>`)
* Holds `uiState` (`MutableStateFlow<EditorUiState>`)
* Handles file operations (`onFilePicked`, `saveCurrentFile`)
* Handles editing operations (`updateParagraph`, `updateSectionHeader`)
* Forwards pagination state to UI via `onPrintPageChanged`

```kotlin
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val recentFileDao: RecentFileDao
) : ViewModel() {

    var documentElements by mutableStateOf<List<DocumentElement>>(emptyList())
        private set

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentData?>(null)
    val currentDocument: StateFlow<DocumentData?> = _currentDocument.asStateFlow()

    // ...
}
```

### Edit Methods

All edit methods follow the same pattern:

```kotlin
fun updateParagraph(index: Int, newContent: AnnotatedString) {
    if (_uiState.value.editorMode != EditorMode.EDIT) return

    val currentList = documentElements.toMutableList()
    if (index in currentList.indices) {
        val element = currentList[index]
        if (element is DocumentElement.Paragraph) {
            currentList[index] = element.copy(
                spans = AnnotatedStringMappers.toTextSpans(
                    annotatedString = newContent,
                    originalSpans = element.spans,
                    listLabel = element.listLabel
                )
            )
            documentElements = currentList
            _editIndex.value = index   // ← signals pending edit
            syncDocumentState()
        }
    }
}
```

The `_editIndex` is a **side channel**: it tells the UI that a change is pending without requiring the ViewModel to hold two copies of the document.

### PrintLayout

The print/render layout. It:

1. Creates the pagination engine (`PrintPaginator` → `IncrementalPaginator`)
2. Collects `editIndex` from the ViewModel
3. Triggers `LaunchedEffect` based on `(documentData, isDarkMode, editIndex)`:
   - If `isNewEdit` → `incrementalUpdate()`
   - Otherwise → `paginate()`
4. Updates `pagesState` with new `PageModel` list (with stable keys + document fingerprint)

```kotlin
LaunchedEffect(documentData, isDarkMode, editIndex) {
    val pendingEdit = editIndex
    val isNewEdit = pendingEdit != null && processedEditIndex != pendingEdit

    val result = withContext(Dispatchers.Default) {
        // ... compute dimensions
        if (isNewEdit) {
            (paginator as IncrementalPaginator).incrementalUpdate(
                previousPages = pages,
                previousData = documentData,
                newData = documentData,
                dimensions = defaultDimensions,
                changedElementIndex = pendingEdit
            )
        } else {
            paginator.paginate(documentData, defaultDimensions, isDarkMode = isDarkMode)
        }
    }
    // ... build stable keys, update pagesState
    processedEditIndex = processedEditIndexState.value
}
```

---

## UI State Shapes

### EditorUiState

```kotlin
data class EditorUiState(
    val documentData: DocumentData? = null,
    val viewMode: ViewMode = ViewMode.MOBILE,
    val editorMode: EditorMode = EditorMode.PREVIEW,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isStarred: Boolean = false,
    val isSuggestChangesEnabled: Boolean = false,
    val isDarkMode: Boolean = false,
    val errorMessage: String? = null,
    val compatibility: CompatibilityLevel? = null,
    val diagnostics: DiagnosticsSummary = DiagnosticsSummary(),
    val outline: List<OutlineEntry> = emptyList(),
    val pageLayout: PageLayoutState = PageLayoutState(),
    val pageCount: Int = 1,
    val currentPage: Int = 1
)
```

### PageModel (from paginator)

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

### PositionedElement

```kotlin
data class PositionedElement(
    val elementId: String,
    val element: DocumentElement,
    val pageIndex: Int,
    val bounds: RectF,
    val indentation: ElementIndentation,
    val metadata: Map<String, Any> = emptyMap(),
    val layoutResult: LayoutResult
)
```

---

## Event → State → Render Cycle

```
┌──────────────┐     event     ┌──────────────┐     compute     ┌──────────────┐
│  User input  │──────────────▶│  ViewModel   │────────────────▶│  New state   │
└──────────────┘               └──────────────┘                 └──────────────┘
       ▲                                                          │
       │                                                          ▼
       └─────────── StateFlow (uiState) ◄─────────────────────────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │  EditorScreen│
                             │  collectAsState()│
                             │  recompose     │
                             └──────────────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │  PrintLayout │
                             │  LaunchedEffect│
                             │  paginator     │
                             │  → PageModel   │
                             └──────────────┘
                                    │
                                    ▼
                             ┌──────────────┐
                             │  PageView    │
                             │  Canvas draw │
                             └──────────────┘
```

---

## Notes on Current State

* The pagination engine runs on `Dispatchers.Default` inside `LaunchedEffect`
* The `delay(200)` debounce is slated for removal in the next migration
* `IncrementalPaginator.incrementalUpdate` is not wired up yet — it needs to be the primary path during editing
* The `LayoutRegistry` cache is shared between `PrintPaginator` and `IncrementalPaginator`
* `print` and `edit` modes share the same `PageModel` structure but render differently
