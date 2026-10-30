# 02 — Principles

Guiding principles that shape TDoc's architecture and guide future migrations. These principles come directly from the wysiwyg editor approach described in the design brief.

---

## 1. Immutable Block List Model (Source of Truth)

The document is modeled as a flat list or tree of discrete **block elements** (Paragraph, Heading, Table, Image, etc.) rather than a raw text buffer.

* **Why:** When a user types a character, you only mutate the single block they are editing. This makes undo/redo and text manipulation instantaneous and predictable.
* **Rule:** Document content is always immutable. A "change" produces a new list of blocks; the old list is discarded.

---

## 2. Stateful ViewModel, Immutable State (UDF — Unidirectional Data Flow)

`EditorViewModel` holds the current state and exposes it via `StateFlow`. UI layers **never mutate state directly** — they emit events (e.g., `onParagraphEdited(index, newContent)`), and the ViewModel computes the new state.

* **Why:** This decouples UI from logic, makes state easy to test, and enables time-travel debugging.
* **Rule:** All state is immutable. All mutations go through ViewModel methods that return new state.

---

## 3. Background Pagination (Off-Main-Thread Measurement)

Text measurement with `StaticLayout` is expensive. Pagination must never block the main thread — otherwise typing will stutter.

* **How:** Run pagination on `Dispatchers.Default`. Cache pre-calculated layouts keyed by `ElementId + StyleHash + ViewportWidth`. Only recalculate the changed block.
* **Rule:** Never measure text on the UI thread.

---

## 4. Custom Canvas Render Pass (Performance)

Do not instantiate thousands of individual Compose `Text` components or Android `View`s for every line of text. Instead, render pages via a single `Canvas` draw pass.

* **How:** `LazyColumn` handles virtualized page scrolling shells. Inside each page, a `Canvas` executes `drawText()`, draws table borders, and paints selection highlights and cursors.
* **Rule:** Text is painted, not composed. This achieves 60–120 FPS even for large documents.

---

## 5. Declarative UI, Imperative Logic

The UI layer is purely declarative (Compose). All complex logic — pagination, caching, selection, undo/redo — lives in the ViewModel or a dedicated domain layer.

* **Why:** Compose is side-effect heavy and not designed for long-running work. Business logic must be pure and testable.
* **Rule:** If a function takes more than a few milliseconds or touches network/storage, it does not belong in a composable.

---

## 6. Single Source of Truth (No Dual Render)

Do not maintain both a "render model" and a "model" that get out of sync. The `PageModel` produced by the paginator is the single source of truth for what the UI draws.

* **Why:** Dual sources of truth cause bugs, especially during incremental updates where stale pages leak through.
* **Rule:** Any change to the document produces a new `List<PageModel>`. The UI always renders what the paginator produced.

---

## 7. Testability by Construction

Every principle above exists to make the architecture testable:

- Immutable blocks → easy to compare states
- Background pagination → can run in unit tests without Android
- Cache → can be measured for cache hits/misses
- UDF → can assert state transitions
