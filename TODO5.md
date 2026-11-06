# TODO 5 — Enter edit mode by double-tapping a preview document

## Goal

In both mobile and print preview, double-tapping the displayed document enters
edit mode. Keep the existing preview edit button as an alternative. The gesture
must not break scrolling, print zoom, or print element selection.

Implement this within the existing Kotlin, Jetpack Compose, and MVVM structure.
Use stable, supported Compose APIs; do not introduce experimental or deprecated
APIs. Keep gesture recognition in the layout composables and the editor-mode
transition in `EditorViewModel`.

## Scope and current project state

This plan is limited to preview-to-edit double-tap behavior. Saving, text-field
flicker, and other editor improvements are outside its scope and are covered
separately in `TODO4.md`.

The current worktree already contains uncommitted changes in the editor UI and
layout files relevant to this behavior. Those changes are user-owned and must be
reviewed and preserved when implementation begins; do not replace them with a
baseline version.

- `EditorScreen.kt` selects `MobileLayout` or `PrintLayout` from `EditorUiState`,
  passes the current editability state to each, and retains the preview edit
  floating action button.
- `EditorViewModel.enterEditMode()` already performs an idempotent transition
  to `EditorMode.EDIT`. Both layouts currently call it from a preview double-tap
  handler. Use this intent rather than adding a second mode transition or
  invoking the generic toggle.
- `MobileLayout.kt` currently installs the gesture only while not editable and
  keys its `pointerInput` with `isEditable`, so the gesture block is recreated
  when the mode changes.
- `PrintLayout.kt` currently handles double taps at the print canvas level:
  preview calls `enterEditMode()`, while edit mode toggles zoom. However, that
  `pointerInput` is keyed with `Unit` while its handler branches on
  `isEditable`. Since `isEditable` is a composable parameter, the long-lived
  pointer handler can retain the mode value from its initial composition. This
  can make subsequent preview/edit double taps take the wrong branch.
- `PageView.kt` handles single-tap element hit-testing only while editable;
  that behavior should remain separate from the preview gesture.
- The project has Compose UI test dependencies and existing Android Compose
  interaction tests. Current editor/layout tests are otherwise primarily unit
  tests; no focused preview double-tap test was found.

## Behavioral requirements

1. A double tap on the document viewport in mobile preview enters edit mode.
2. A double tap on the document viewport in print preview enters edit mode and
   does not zoom instead.
3. A double tap while already in edit mode must not re-enter edit mode. Preserve
   the current print edit-mode double-tap zoom behavior unless interaction
   testing shows it conflicts with editing; pinch/pan behavior must remain
   available.
4. A single tap in mobile preview does not enter edit mode. Scrolling the mobile
   document continues to work normally.
5. In print edit mode, a single tap on a text element continues to select that
   element for editing; tapping elsewhere continues to clear that selection as
   it does today.
6. The existing preview edit button remains available and continues to enter
   edit mode.
7. The mode transition must not close/reload the document or reset the current
   mobile scroll position, print page position, or print zoom/pan state.
8. Gestures are limited to the document area. Toolbar buttons, menus, and the
   floating action button retain their independent tap behavior.

## Implementation plan

### 1. Confirm and preserve the current working changes

1. Before editing, inspect the existing diff in `EditorScreen.kt`,
   `EditorViewModel.kt`, `MobileLayout.kt`, `PrintLayout.kt`, and
   `DocumentElementRenderer.kt`. Keep all unrelated or user-owned changes intact.
2. Confirm that `EditorViewModel.enterEditMode()` remains the single
   idempotent action used by the preview button and both layout gestures.
3. Keep this task focused on gesture behavior; do not refactor the save flow,
   renderer state, or print pagination as part of this change.

### 2. Make print gesture mode state current

1. In `PrintLayout.kt`, ensure the double-tap handler always branches on the
   current `isEditable` value. Prefer keying `pointerInput` with that value,
   consistent with `MobileLayout`, or use an established Compose pattern that
   safely reads the latest value without restarting unrelated state.
2. Keep preview double-tap dispatching only `viewModel.enterEditMode()`. Do not
   call `toggleEditorMode()` from a preview gesture, because that action could
   exit edit mode if invoked after recomposition or through another UI path.
3. Preserve the existing edit-mode zoom target, animation, and reset of pan
   offset. Ensure a mode change does not recreate `listState`, zoom state, pan
   state, or the pagination machinery.
4. Verify that `PageView`'s editable single-tap hit-testing does not consume or
   intercept preview double taps. Keep that hit-testing enabled only in edit
   mode and avoid adding competing preview gesture handlers to each page unless
   testing establishes that the parent handler cannot reliably receive taps.

### 3. Keep mobile gestures mode-aware and non-disruptive

1. Keep the mobile gesture attached to the document list/viewport only while
   preview is active. Its existing `pointerInput(isEditable)` key should ensure
   the handler is removed on entry to edit mode.
2. Dispatch the same idempotent `enterEditMode()` ViewModel action used by
   print and the preview button.
3. Confirm that the gesture recognizer does not prevent normal `LazyColumn`
   drags, item interaction, or scrolling. Do not add a single-tap mode switch.
4. Keep the existing stable item keys and remembered list state so the
   preview-to-edit transition preserves the reader's location.

### 4. Keep responsibilities in the existing MVVM layers

- Layout composables recognize pointer gestures and send a semantic action to
  the ViewModel; they do not own editor-mode state or duplicate transition
  logic.
- `EditorViewModel` remains responsible for changing `EditorUiState.editorMode`.
  The transition is idempotent and does not perform repository or persistence
  work.
- `EditorScreen` continues to provide the current mode to the selected layout
  and exposes the preview edit button as a non-gesture alternative.
- Do not add a new gesture abstraction, state store, repository method, or
  dependency solely for this behavior.

## Test and verification plan

### Automated interaction coverage

Add focused Compose UI tests using the existing Android Compose UI test setup.
Prefer testing the layout transition through its real mode state/ViewModel
intent, without adding production-only test hooks.

1. **Mobile preview double tap:** render mobile content in preview, double-tap
   the document, and assert the ViewModel/UI state enters edit mode.
2. **Mobile controls and scrolling:** verify a single tap does not enter edit
   mode, a swipe still scrolls, and the preview edit button still enters edit
   mode.
3. **Print preview double tap:** render print content in preview, double-tap
   the page/document viewport, and assert edit mode is entered rather than the
   zoom scale being changed.
4. **Mode updates:** enter edit mode, then return to preview and double-tap
   again; verify the print handler reads the new mode rather than a stale
   closure. Also verify the edit-mode double tap retains its intended zoom
   behavior.
5. **Print element selection:** in edit mode, verify a single tap on text keeps
   selecting the active element and a tap outside it clears the selection.
6. **State continuity:** where practical, verify that changing editor mode
   keeps the same document open and does not reset list/page position or print
   zoom/pan.

### Build and manual checks

1. Run the focused Android Compose interaction test class(es), then the
   smallest relevant unit-test selector if any ViewModel transition test is
   added.
2. Run the project's debug compilation/test task needed to validate the
   changed Compose code and tests.
3. On a device or emulator, open a document in each view mode and check:
   - mobile: double tap enters edit; single tap and scroll remain natural;
   - print: double tap in preview enters edit; print edit-mode zoom and
     single-tap selection continue to work;
   - the preview button still enters edit mode;
   - changing mode does not close the document or jump to another page/position.
4. Test repeated preview/edit transitions, including switching from mobile to
   print and back, to catch stale gesture closures.

## Acceptance criteria

- Double-tapping document content in mobile preview enters edit mode.
- Double-tapping document content in print preview enters edit mode rather than
  changing zoom.
- Mode changes are handled by the ViewModel's idempotent edit-entry action.
- Mobile scrolling, print edit-mode zoom, print element selection, and the
  existing preview edit button retain their behavior.
- The document and current view position remain intact across the transition.
- The implementation stays within the existing MVVM/Compose architecture and
  uses stable, non-deprecated APIs.
