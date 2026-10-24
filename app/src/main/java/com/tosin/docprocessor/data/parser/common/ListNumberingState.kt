package com.tosin.docprocessor.data.parser.common

/**
 * Tracks sequential item counters while a document body is walked, so list
 * markers render as 1, 2, 3… instead of every item showing the level's start
 * value. Pure and JVM-testable.
 *
 * Keys are the numbering identity of the list: DOCX uses `w:numId`, ODF uses
 * the list style name (ODF continues numbering across sibling lists that share
 * a style, which matches the ODF default — `text:restart` resets it).
 *
 * Counters are per level: appending an item at level L increments L and
 * resets all deeper levels, so nested lists restart for every parent item.
 */
class ListNumberingState {

    private val counters = mutableMapOf<String, IntArray>()

    /**
     * Registers the next item of [key] at [level] (0-based) and returns its
     * 1-based sequence index within that level. Callers apply the level's
     * start value: `value = start + (sequence - 1)`.
     */
    fun nextItem(key: String, level: Int): Int {
        val safeLevel = level.coerceIn(0, ListMarkerFormatter.MAX_LEVELS - 1)
        val counts = counters.getOrPut(key) { IntArray(ListMarkerFormatter.MAX_LEVELS) }
        counts[safeLevel] += 1
        for (deeper in (safeLevel + 1) until ListMarkerFormatter.MAX_LEVELS) {
            counts[deeper] = 0
        }
        return counts[safeLevel]
    }

    /**
     * Restarts [key] at [level] (default the outermost), resetting that level
     * and everything below it. Used by ODF `text:restart` / `text:start`
     * overrides and available to DOCX `w:lvlOverride` semantics.
     */
    fun restart(key: String, level: Int = 0, at: Int = 0) {
        val safeLevel = level.coerceIn(0, ListMarkerFormatter.MAX_LEVELS - 1)
        val counts = counters.getOrPut(key) { IntArray(ListMarkerFormatter.MAX_LEVELS) }
        counts[safeLevel] = at
        for (deeper in (safeLevel + 1) until ListMarkerFormatter.MAX_LEVELS) {
            counts[deeper] = 0
        }
    }
}
