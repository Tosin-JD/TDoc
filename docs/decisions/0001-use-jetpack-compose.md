# 0001 — Use Jetpack Compose

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

The TDoc editor was originally built with XML layouts and MVC-style code. As the app grew (rich text editing, pagination, theme switching, dark mode), the XML-based UI became:

- Hard to reason about (imperative draw calls mixed with layout XML)
- Difficult to animate (no declarative state)
- Poor at handling theme switching (manual view attribute changes)

The design brief specified that the app must use **Jetpack Compose** for all UI.

## Decision

Use **Jetpack Compose** as the single UI framework for the entire app, including the editor, home screen, settings, and dialogs.

## Consequences

**Easier:**
- Declarative UI: state → UI is a pure function
- Theme switching is a simple recomposition
- State management is unified (all in `ViewModel` + `StateFlow`)
- Hot reload works naturally for UI iteration
- No XML inflation overhead or view hierarchy bloating

**Harder:**
- Steeper learning curve for developers used to XML
- Initial setup is more verbose (composables vs. inflate + findViewById)
- Limited third-party libraries support (some Android libraries only target View)

**What we gave up:**
- XML layouts, View system, `findViewById`, `onClickListener` (legacy)
- Some legacy Android UI libraries that only support View
