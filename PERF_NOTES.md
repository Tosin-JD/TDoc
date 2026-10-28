# Performance Notes

## Phase P0 - Completed

### P0-1: Native TextMeasurer using Paint
- **Status**: Implemented
- **Changes**: Added TypefaceResolver, TextPaintCache, NativeTextMeasurer; extended TextMeasurer with measureWidth and caching
- **Expected**: 10x faster measurement vs Compose TextMeasurer
- **Acceptance**: Infrastructure ready

### P0-2: Single editable overlay
- **Status**: Implemented (single BasicTextField/TextFieldState overlay)
- **Changes**: Created EditableTextOverlay, replaced EditablePrintOverlay usage in PageView
- **Expected**: Reduced memory/composition nodes, better IME support
- **Acceptance**: Implemented

### P0-3: Pages state without flicker
- **Status**: Implemented
- **Changes**: Replaced produceState with mutableStateOf + LaunchedEffect + delay debounce (200ms)
- **Expected**: No flicker during pagination
- **Acceptance**: Implemented

### P0-4: Stable LazyColumn keys
- **Status**: Implemented
- **Changes**: Added stableKey/documentFingerprint to PageModel, use stableKey in LazyColumn items
- **Expected**: No full re-renders on reflow
- **Acceptance**: Implemented

### P0-5: Clip via DrawScope.clipRect
- **Status**: Implemented
- **Changes**: Replaced nativeCanvas clip with clipRect, added try/catch around render
- **Expected**: Leak-safe clipping
- **Acceptance**: Implemented

### P0-6: Consistent scaling
- **Status**: Implemented
- **Changes**: Created PageScaler, added isDarkMode support to PageView/PrintLayout, consistent scaling logic
- **Expected**: No margin inconsistencies
- **Acceptance**: Implemented

### P0-7: Font fallback / CJK
- **Status**: Infrastructure added
- **Changes**: TypefaceResolver with fallback support
- **Expected**: CJK support ready
- **Acceptance**: Pending font assets

## Phase P0 Summary
- **Files added**: TypefaceResolver.kt, TextPaintCache.kt, NativeTextMeasurer.kt, EditableTextOverlay.kt, PageScaler.kt
- **Files modified**: TextMeasurer.kt, PageModel.kt, PageView.kt, PrintLayout.kt
- **Tests added**: None yet
- **Build**: Not run in this environment
- **Next**: Phase P1
