# Naming Conventions

Consistent naming makes the codebase predictable and searchable.

---

## Packages

```
com.tosin.docprocessor.<layer>.<feature>

- com.tosin.docprocessor.ui       ← Presentation layer
- com.tosin.docprocessor.domain   ← Domain logic (models, rules)
- com.tosin.docprocessor.data     ← Data layer (repositories, parsers)
- com.tosin.docprocessor.core     ← Core utilities
- com.tosin.docprocessor.di       ← Dependency injection
```

### Subpackages

| Package | Purpose |
|---|---|
| `ui.editor` | Editor screen, ViewModel, UI state |
| `ui.editor.layouts` | Layout components (print, mobile) |
| `ui.editor.layouts.print` | Print layout pipeline |
| `domain.pagination` | Pagination engine |
| `domain.document` | Document data types |
| `domain.model` | Layout models (PageModel, PositionedElement) |
| `domain.typography` | Font and text styling |
| `data.repository` | Data repositories |
| `data.parser` | Document parsers (DOCX, ODT) |
| `core.rendering` | Rendering utilities |
| `core.color`, `core.text` | Core utilities |

---

## Classes and Interfaces

| Element | Convention | Example |
|---|---|---|
| Class | PascalCase (noun) | `EditorViewModel`, `PrintPaginator` |
| Interface | PascalCase (noun) | `Paginator`, `DocumentRepository` |
| Sealed interface | PascalCase (noun) | `DocumentElement`, `PageState` |
| Object | PascalCase | `PaginationEngineFactory` |
| Enum | PascalCase | `EditorMode`, `HeaderFooterKind` |

### Examples

```kotlin
// Domain
sealed interface DocumentElement
data class Paragraph(...) : DocumentElement
data class Table(...) : DocumentElement

// Presentation
@HiltViewModel
class EditorViewModel @Inject constructor(...)

@Composable
fun PageView(...)
fun PrintLayout(...)
fun EditorScreen(...)
```

---

## Functions

| Element | Convention | Example |
|---|---|---|
| Function | camelCase | `paginateDocument`, `updateParagraph` |
| Method | camelCase | `incrementalUpdate`, `renderBlock` |
| Factory | PascalCase | `createPaginator`, `createTextMeasurer` |

### Examples

```kotlin
// Domain
fun paginateDocument(...) = withContext(Dispatchers.Default) { ... }
fun incrementalUpdate(...) = ...

// Presentation
fun updateParagraph(index: Int, newContent: AnnotatedString) { ... }
fun updateSectionHeader(index: Int, newText: String) { ... }

// Rendering
@Composable
fun EditableTextOverlay(...) { ... }
```

---

## Properties and Variables

| Type | Convention | Example |
|---|---|---|
| `val` | camelCase | `currentDocument`, `editIndex` |
| `var` | camelCase | `documentElements` |
| Private property | camelCase with `_` prefix in ViewModel | `_uiState`, `_events` |
| Extension property | camelCase | `isFlowable()`, `isAtomic()` |

### StateFlow Patterns

```kotlin
// Private MutableStateFlow for internal state
private val _uiState = MutableStateFlow(EditorUiState())
val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

// Public StateFlow for UI consumption
private val _editIndex = MutableStateFlow<Int?>(null)
val editIndex: StateFlow<Int?> = _editIndex.asStateFlow()
```

---

## Constants

| Type | Convention | Example |
|---|---|---|
| Top-level constant | SCREAMING_SNAKE_CASE | `PRINT_PREVIEW_SCALE = 0.82f` |
| Navigation key | camelCase | `ViewMode.MOBILE`, `EditorMode.EDIT` |
| Magic numbers | Avoid — use named constants | `760.dp` → `maxOf(maxWidth, 760.dp)` |

### Examples

```kotlin
private const val PRINT_PREVIEW_SCALE = 0.82f
private const val PRINT_PREVIEW_DEBOUNCE_MS = 200L
```

---

## File Names

| Type | Convention | Example |
|---|---|---|
| Single class | `<ClassName>.kt` | `EditorViewModel.kt` |
| Multiple top-level declarations | `<Feature>.kt` | `AnnotatedStringMappers.kt` |
| Test class | `<ClassUnderTest>Test.kt` | `PaginationEngineFactoryTest.kt` |
| Test file | `<Feature>Test.kt` | `FlowControllerTest.kt` |

### File Location by Layer

| Layer | Path | Example |
|---|---|---|
| Presentation | `app/src/main/java/.../ui/...` | `EditorViewModel.kt` |
| Domain | `app/src/main/java/.../domain/...` | `Paginator.kt` |
| Data | `app/src/main/java/.../data/...` | `DocumentRepositoryImpl.kt` |
| Test (unit) | `app/src/test/java/...` | `PaginationEngineFactoryTest.kt` |
| Test (instrumentation) | `app/src/androidTest/java/...` | `EditorScreenTest.kt` |

---

## Documentation Comments

Use KDoc for public API:

```kotlin
/**
 * Perform an incremental update on existing pagination.
 *
 * @param previousPages The existing pagination from before the edit
 * @param previousData The previous document data
 * @param newData The updated document data
 * @param dimensions Page dimensions
 * @param changedElementIndex Index of the changed element in newData
 * @param isDarkMode Whether dark mode is active (affects header/footer rendering)
 * @return Updated page list (minimal re-calculation)
 */
fun incrementalUpdate(
    previousPages: List<PageModel>,
    previousData: DocumentData,
    newData: DocumentData,
    dimensions: PageDimensions,
    changedElementIndex: Int,
    isDarkMode: Boolean = false
): List<PageModel>
```

---

## Test Naming

| Type | Convention | Example |
|---|---|---|
| Test class | `<ClassUnderTest>Test` | `PaginationEngineFactoryTest` |
| Test method | `should_<expected_behavior>` | `should_reparse_pages_after_height_change()` |
| Test data | `<scenario>_<input>` | `empty_document`, `document_with_header` |

```kotlin
class PaginationEngineFactoryTest {
    @Test
    fun `createPaginator returns IncrementalPaginator when useIncrementalUpdates is true`() = runTest {
        // Arrange
        // Act
        // Assert
    }
}
```

---

## Other Conventions

- **One top-level class per file**
- **No wildcard imports** (`import com.tosin.docprocessor.*` is discouraged)
- **Prefer `when` over `if/else`** for enum-based logic
- **Use `runCatching` for error handling** in repository layer
- **Use `@OptIn(ExperimentalApi::class)`** instead of `@Suppress("DEPRECATION")` when possible
