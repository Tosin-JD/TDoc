# 0002 — Choose MVI Over MVVM

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

TDoc's editor needs to handle:
- Unidirectional data flow (UDF) for state management
- Live editing with incremental pagination
- Immutable document model
- Debugging (state history, replay)

The classic `MVVM` pattern (ViewModel + LiveData + Observer) does not fit the requirements:

- **LiveData** is lifecycle-aware but not immutable
- **Forgettable** (no easy state history)
- **Does not enforce immutability** by construction
- The app already uses `StateFlow` for UI state, which is the modern Kotlin approach

MVI (Model-View-Intent) was considered, but for this mature codebase it would require significant refactoring.

## Decision

Use **UDF (Unidirectional Data Flow)** pattern on top of `StateFlow`:

1. **UI → ViewModel:** Events (method calls like `onParagraphEdited`)
2. **ViewModel → UI:** `StateFlow<EditorUiState>` (immutable state snapshot)
3. **Side effects:** `viewModelScope.launch` for pagination, file I/O
4. **Domain logic:** Pure functions (pagination, measurement, caching)

This is a pragmatic MVI variant — the "Intent" is implicit (method parameters), not an explicit sealed class.

## Consequences

**Easier:**
- `StateFlow` is the standard Kotlin reactivity primitive
- Immutable `EditorUiState` makes state history trivial
- Easy to test (no lifecycle dependencies, pure state transitions)
- Incremental pagination fits naturally: event → compute new state → emit

**Harder:**
- State objects can become large as the app grows
- Requires discipline to not mutate state directly
- Side effects must be explicitly routed through the ViewModel

**What we gave up:**
- Classic MVVM (LiveData + Observer) patterns
- AutoDispose (managed by Hilt + viewModelScope)
