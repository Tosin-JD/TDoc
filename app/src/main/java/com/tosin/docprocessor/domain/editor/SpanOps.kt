package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.parser.internal.models.TextSpan

/**
 * A character-range selection over the plain text of a block. Offsets are
 * UTF-16 code-unit indices, matching Compose text selection semantics.
 */
data class Selection(val start: Int, val end: Int) {
    val isCollapsed: Boolean get() = start == end

    fun sorted(): Selection = if (start <= end) this else Selection(end, start)
}

enum class SpanProperty {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH
}

/**
 * Pure span math: offset resolution, range application, and toggling.
 * This is the highest-risk logic in the app; keep it free of any framework type.
 */
object SpanOps {

    fun spansText(spans: List<TextSpan>): String = spans.joinToString("") { it.text }

    private data class Segment(val start: Int, val end: Int, val span: TextSpan)

    private fun segments(spans: List<TextSpan>): List<Segment> {
        val result = mutableListOf<Segment>()
        var cursor = 0
        for (span in spans) {
            if (span.text.isEmpty()) continue
            result += Segment(cursor, cursor + span.text.length, span)
            cursor += span.text.length
        }
        return result
    }

    private fun TextSpan.sameStyle(other: TextSpan): Boolean =
        copy(text = "") == other.copy(text = "")

    private fun List<TextSpan>.mergeAdjacent(): List<TextSpan> = buildList {
        for (span in this@mergeAdjacent) {
            val last = lastOrNull()
            if (last != null && last.sameStyle(span)) {
                this[lastIndex] = last.copy(text = last.text + span.text)
            } else {
                add(span)
            }
        }
    }

    private fun TextSpan.slice(start: Int, end: Int): TextSpan =
        copy(text = text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))

    fun propertyActive(span: TextSpan, property: SpanProperty): Boolean = when (property) {
        SpanProperty.BOLD -> span.isBold
        SpanProperty.ITALIC -> span.isItalic
        SpanProperty.UNDERLINE -> span.isUnderline
        SpanProperty.STRIKETHROUGH -> span.isStrikethrough
    }

    fun applyProperty(span: TextSpan, property: SpanProperty, value: Boolean): TextSpan =
        when (property) {
            SpanProperty.BOLD -> span.copy(isBold = value)
            SpanProperty.ITALIC -> span.copy(isItalic = value)
            SpanProperty.UNDERLINE -> span.copy(isUnderline = value)
            SpanProperty.STRIKETHROUGH -> span.copy(isStrikethrough = value)
        }

    private fun middleSpans(spans: List<TextSpan>, selection: Selection): List<TextSpan> {
        val sel = selection.sorted()
        return segments(spans)
            .filter { it.end > sel.start && it.start < sel.end }
            .map { it.span }
    }

    /**
     * Applies [transform] to the region covered by [selection]; spans outside the
     * region are preserved. Result segments are re-merged when their style is equal.
     */
    fun applyRange(
        spans: List<TextSpan>,
        selection: Selection,
        transform: (TextSpan) -> TextSpan
    ): List<TextSpan> {
        val sel = selection.sorted()
        if (sel.isCollapsed) {
            return spans.map(transform).mergeAdjacent()
        }

        val output = mutableListOf<TextSpan>()
        for (segment in segments(spans)) {
            val hitStart = maxOf(segment.start, sel.start)
            val hitEnd = minOf(segment.end, sel.end)
            if (hitEnd <= hitStart) {
                output += segment.span
                continue
            }
            if (segment.start < hitStart) {
                output += segment.span.slice(0, hitStart - segment.start)
            }
            output += transform(segment.span.slice(hitStart - segment.start, hitEnd - segment.start))
            if (hitEnd < segment.end) {
                output += segment.span.slice(hitEnd - segment.start, segment.end - segment.start)
            }
        }
        return output.mergeAdjacent()
    }

    /**
     * Toggles a format property over the given selection. When the selection is
     * collapsed the whole paragraph is toggled. If any part of the target region
     * is not yet formatted the region becomes formatted (and vice versa).
     */
    fun toggle(
        spans: List<TextSpan>,
        selection: Selection,
        property: SpanProperty
    ): List<TextSpan> {
        val sel = selection.sorted()
        val middle = middleSpans(spans, sel)
        val target = middle.any { !propertyActive(it, property) }
        return applyRange(spans, sel) { applyProperty(it, property, target) }
    }

    /**
     * Whether the selection/paragraph currently has the property active. Used by
     * the toolbar to highlight active buttons.
     */
    fun isPropertyActive(
        spans: List<TextSpan>,
        selection: Selection,
        property: SpanProperty
    ): Boolean {
        val sel = selection.sorted()
        if (sel.isCollapsed) {
            val caret = sel.start
            val segment = segments(spans).firstOrNull { caret in it.start until it.end }
                ?: return false
            segments@ return propertyActive(segment.span, property)
        }
        val middle = middleSpans(spans, sel)
        return middle.isNotEmpty() && middle.all { propertyActive(it, property) }
    }
}