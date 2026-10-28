# Testing Conventions

TDoc uses a combination of unit tests (JVM) and instrumentation tests (Android) to ensure correctness.

---

## Test Types

| Type | Location | Tool |
|---|---|---|
| Unit tests (JVM) | `app/src/test/java/...` | JUnit 4, Kotlinx Coroutines Test |
| Instrumentation tests | `app/src/androidTest/java/...` | AndroidJUnit4, Espresso |
| Integration tests | `app/src/androidTest/java/...` | AndroidJUnit4, Espresso |

---

## Test Locations

```
app/src/test/                          ← JVM unit tests (can run on CI)
├── java/com/tosin/docprocessor/
│   ├── ExampleUnitTest.kt
│   ├── ui/editor/renderer/            ← Renderer tests
│   ├── ui/components/table/           ← Table tests
│   ├── domain/pagination/             ← Pagination tests
│   ├── data/parser/parser/            ← Parser tests
│   └── ...
└── androidTest/                       ← Instrumentation tests
    └── java/com/tosin/docprocessor/
        ├── ExampleInstrumentedTest.kt
        ├── ui/editor/                   ← Editor screen tests
        ├── ui/editor/renderer/          ← Renderer tests
        └── ...
```

---

## Test Naming Conventions

### Test Class

- `<ClassUnderTest>Test`

```kotlin
class PaginationEngineFactoryTest
class EditorViewModelTest
class DocumentElementTest
```

### Test Method

- `should_<expected_behavior>`

```kotlin
@Test
fun `createPaginator returns IncrementalPaginator when useIncrementalUpdates is true`() = runTest {
    // ...
}

@Test
fun `should_repaginate_pages_after_height_change`() = runTest {
    // ...
}
```

### Test Data

Use descriptive names with underscores:

```kotlin
val emptyDocument = DocumentData(content = emptyList())
val twoParagraphDocument = DocumentData(
    content = listOf(paragraph1, paragraph2)
)
```

---

## What to Test

### Pagination Engine

| Test | What to verify |
|---|---|
| `paginate()` on empty document | Returns single empty page |
| `paginate()` splits paragraph across pages | Paragraph fragments correctly |
| `paginate()` with table | Table stays on one page (atomic) |
| `incrementalUpdate()` after height change | Only affected pages change |
| `incrementalUpdate()` detects structural change | Full pagination on insert/delete |
| `LayoutRegistry` cache | `put()`, `get()`, hash mismatch invalidation |

### TextMeasurer

| Test | What to verify |
|---|---|
| `measureParagraph()` | Returns correct height |
| `buildParagraphLayout()` | Layout matches expected spans |
| Cache hit/miss | Height reused without re-measurement |

### EditorViewModel

| Test | What to verify |
|---|---|
| `updateParagraph()` | Document state changes correctly |
| `updateSectionHeader()` | Header text updates |
| `onFilePicked()` | Loads document, updates state |
| `closeDocument()` | Resets state |

### UI

| Test | What to verify |
|---|---|
| `EditorScreen` | Navigation, state display, diagnostics |

---

## Async Testing

Use `kotlinx.coroutines.test` for testing coroutines:

```kotlin
@Test
fun `createPaginator returns IncrementalPaginator when useIncrementalUpdates is true`() = runTest {
    val factory = PaginationEngineFactory(Context.createMockContext())
    val paginator = factory.createPaginator(useIncrementalUpdates = true)
    
    assertThat(paginator).isInstanceOf(IncrementalPaginator::class.java)
}
```

For ViewModel tests, use `viewModelScope` with `TestDispatcher`:

```kotlin
@get:Rule
val testDispatcher = StandardTestDispatcher()

@Before
fun setUp() {
    Dispatchers.setMain(testDispatcher)
}

@After
fun tearDown() {
    Dispatchers.resetMain()
}
```

---

## Running Tests

### Unit Tests (JVM)

```bash
./gradlew test
```

### Instrumentation Tests

```bash
./gradlew connectedAndroidTest
```

### Single Test

```bash
./gradlew test --tests "com.tosin.docprocessor.domain.pagination.PaginationEngineFactoryTest"
```

---

## Coverage Goals

| Component | Target Coverage |
|---|---|
| `Paginator` interface | 90%+ |
| `PrintPaginator` | 80%+ |
| `IncrementalPaginator` | 80%+ |
| `LayoutRegistry` | 90%+ |
| `TextMeasurer` | 80%+ |
| `EditorViewModel` | 70%+ |
| UI components | 50%+ (key flows) |
