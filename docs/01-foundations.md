# TDoc — Documentation

This is the architecture and development documentation for **TDoc**, an Android word processor that supports editing, previewing, and exporting documents (DOCX/ODT) in a native Android experience.

---

## What This Documentation Is For

This is a **living reference** for understanding, extending, and eventually migrating TDoc's editor to a modern, stateless, declarative WYSIWYG architecture. It grew out of a migration effort from a mixed-view-based approach (XML layouts + MVC-style code) to a fully Compose-based, unidirectional data flow architecture.

## How to Use This Documentation

1. **Read `01-foundations.md` and `02-principles.md`** first — they define the mental model.
2. **Read `architecture/overview.md`** to see how the layers connect.
3. **Read `decisions/`** to understand why specific technologies were chosen.
4. **Use `conventions/`** when writing or reviewing code so the codebase stays consistent.

## Current State of the Codebase

TDoc currently uses a **hybrid architecture**:

- UI: Jetpack Compose for new screens (`EditorScreen`, `HomeScreen`)
- Some legacy-style state management in `EditorViewModel` using `mutableStateOf`
- Pagination engine using `PrintPaginator` + `IncrementalPaginator` with a `LayoutRegistry` cache
- No formal MVVM/MVI data flow boundaries yet

The long-term goal is to migrate to a **pure declarative, stateless, UDF-based** editor similar to what is described in `01-foundations.md` and `02-principles.md`.

---

## Key Code Locations

| Component | Path |
|---|---|
| App entry point | `app/src/main/java/com/tosin/docprocessor/MainActivity.kt` |
| Root composable | `app/src/main/java/com/tosin/docprocessor/ui/AppRoot.kt` |
| Editor screen | `app/src/main/java/com/tosin/docprocessor/ui/editor/EditorScreen.kt` |
| Editor ViewModel | `app/src/main/java/com/tosin/docprocessor/ui/editor/EditorViewModel.kt` |
| Print layout | `app/src/main/java/com/tosin/docprocessor/ui/editor/layouts/print/PrintLayout.kt` |
| Page view | `app/src/main/java/com/tosin/docprocessor/ui/editor/layouts/print/PageView.kt` |
| Pagination engine | `app/src/main/java/com/tosin/docprocessor/domain/pagination/` |
| Document models | `app/src/main/java/com/tosin/docprocessor/domain/model/` |
| Document data | `app/src/main/java/com/tosin/docprocessor/domain/document/` |
