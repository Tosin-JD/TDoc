# Code Style

TDoc follows Kotlin and Android best practices. This document defines the conventions for the codebase.

---

## Kotlin Style

### Naming

| Element | Convention | Example |
|---|---|---|
| Package | `com.tosin.docprocessor.<layer>.<feature>` | `com.tosin.docprocessor.domain.pagination` |
| Class / Interface | PascalCase | `EditorViewModel`, `PrintPaginator` |
| Object | PascalCase | `PaginationEngineFactory` |
| Function | camelCase | `paginateDocument`, `updateParagraph` |
| Property / Parameter | camelCase | `documentElements`, `changedElementIndex` |
| Constant | SCREAMING_SNAKE_CASE | `PRINT_PREVIEW_SCALE` |
| Type alias | PascalCase | `EditorUiState`, `PositionedElement` |

### File Naming

- One top-level class per file
- File name matches class name (PascalCase)
- Example: `EditorViewModel.kt`, `PrintPaginator.kt`

### Formatting

- Indentation: 4 spaces (no tabs)
- Line length: ~120 characters
- Use Kotlin's automatic formatter (`./gradlew spotlessApply` if available)

---

## Kotlin Specifics

### Immutability

- Prefer `val` over `var`
- Use immutable collections (`List`, `Map`, `Set`)
- Mark ViewModel state as `private set`

```kotlin
var documentElements by mutableStateOf<List<DocumentElement>>(emptyList())
    private set  // ← immutability enforced
```

### Sealed Interfaces

Use sealed interfaces for domain models that share a common type:

```kotlin
sealed interface DocumentElement {
    val id: String
    data class Paragraph(...) : DocumentElement
    data class Table(...) : DocumentElement
    // ...
}
```

### Extension Functions

Use extension functions to add behavior to existing types:

```kotlin
fun DocumentElement.isFlowable(): Boolean = when (this) {
    is DocumentElement.Paragraph -> true
    is DocumentElement.SectionHeader -> true
    is DocumentElement.Section -> true
    else -> false
}
```

---

## Compose Style

### Composable Functions

- Composables must be `fun` with `@Composable` annotation
- Use `@Composable` for UI logic, not business logic
- Keep composables small and focused

```kotlin
@Composable
fun PageView(
    pageModel: PageModel,
    pageCount: Int,
    renderer: PrintElementRenderer,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false
) {
    // UI logic only — pagination is done in PrintLayout
}
```

### State Collection

```kotlin
val uiState by viewModel.uiState.collectAsState()
val documentData by viewModel.currentDocument.collectAsState()
```

### Side Effects

Use `LaunchedEffect` for any background work:

```kotlin
LaunchedEffect(documentData, isDarkMode, editIndex) {
    // Pagination runs on Dispatchers.Default
}
```

---

## Naming Conventions

| Concept | Prefix / Convention | Example |
|---|---|---|
| ViewModel | `EditorViewModel`, `HomeViewModel` | `EditorViewModel` |
| Composable | `PageView`, `HomeScreen`, `PrintLayout` | `PageView` |
| Paginator | `PrintPaginator`, `IncrementalPaginator` | `PrintPaginator` |
| Repository | `DocumentRepository`, `RecentFileDao` | `DocumentRepositoryImpl` |
| Parser | `DocxParser`, `OdtParser`, `ParserFactory` | `OdtDocumentParser` |
| Models | `DocumentElement`, `PageModel`, `PositionedElement` | `DocumentElement` |
| State | `EditorUiState`, `PageLayoutState` | `EditorUiState` |
| Events | `onParagraphEdited`, `onFilePicked` | `updateParagraph` |

---

## Testing Conventions

### Test Locations

| Type | Location | Example |
|---|---|---|
| Unit tests (common) | `app/src/test/java/...` | `PaginationEngineFactoryTest` |
| Instrumentation tests | `app/src/androidTest/java/...` | `EditorScreenTest` |

### Test Naming

- Test class name: `<ClassUnderTest>Test`
- Test method name: `should_<expected_behavior>`

```kotlin
class PaginationEngineFactoryTest {
    @Test
    fun `createPaginator returns IncrementalPaginator when useIncrementalUpdates is true`() = runTest {
        // ...
    }
}
```

### What to Test

| Layer | What to test |
|---|---|
| `Paginator` | `paginate()`, `estimatePageCount()`, `clearCache()` |
| `IncrementalPaginator` | `incrementalUpdate()`, `detectStructuralChange()` |
| `LayoutRegistry` | `put()`, `get()`, `invalidate()`, `clear()` |
| `TextMeasurer` | `measureParagraph()`, `buildParagraphLayout()` |
| `EditorViewModel` | `updateParagraph()`, `updateSectionHeader()`, state transitions |
| UI | `EditorScreen` navigation, state display |
