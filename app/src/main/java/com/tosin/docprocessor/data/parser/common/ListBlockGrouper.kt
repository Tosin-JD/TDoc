package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListStyle

/**
 * Groups consecutive list paragraphs into nested [BlockNode.ListBlock]s,
 * preserving the `ilvl` hierarchy: paragraphs deeper than the run's base level
 * become nested lists inside the item that precedes them. Pure and
 * JVM-testable; format-neutral (the DOCX parser uses it today, ODT already
 * recurses in its mapper).
 */
object ListBlockGrouper {

    /**
     * @param styleFor resolves the [ListStyle] (levels, kind) for a numbering
     *        identity reported by [BlockNode.ListMarker.numId]
     */
    fun group(
        blocks: List<BlockNode>,
        styleFor: (numId: String?) -> ListStyle = { ListStyle() }
    ): List<BlockNode> {
        val output = mutableListOf<BlockNode>()
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            val marker = (block as? BlockNode.Paragraph)?.listMarker
            if (marker == null) {
                output += block
                index++
                continue
            }
            val run = mutableListOf<BlockNode.Paragraph>()
            while (index < blocks.size) {
                val paragraph = blocks[index] as? BlockNode.Paragraph ?: break
                val currentMarker = paragraph.listMarker ?: break
                // A different list identity or kind starts a new list.
                if (currentMarker.numId != marker.numId || currentMarker.ordered != marker.ordered) break
                run += paragraph
                index++
            }
            if (run.isEmpty()) {
                output += block
                index++
                continue
            }
            output += buildList(run, styleFor)
        }
        return output
    }

    private fun buildList(
        paragraphs: List<BlockNode.Paragraph>,
        styleFor: (String?) -> ListStyle
    ): BlockNode.ListBlock {
        val baseLevel = paragraphs.minOf { it.listMarker!!.level }
        // Malformed runs may open with a deep level; re-anchor on the true minimum.
        val items = mutableListOf<BlockNode.ListItem>()
        var pendingNested = mutableListOf<BlockNode.Paragraph>()

        fun flushNested() {
            if (pendingNested.isEmpty()) return
            val nested = buildList(pendingNested.toList(), styleFor)
            pendingNested = mutableListOf()
            if (items.isNotEmpty()) {
                val last = items.removeAt(items.lastIndex)
                items += last.copy(blocks = last.blocks + nested)
            } else {
                // Deep-level paragraphs before any base item: keep them as a list of their own.
                items += BlockNode.ListItem(blocks = listOf(nested))
            }
        }

        for (paragraph in paragraphs) {
            val marker = paragraph.listMarker!!
            if (marker.level == baseLevel) {
                flushNested()
                items += BlockNode.ListItem(
                    blocks = listOf(paragraph),
                    marker = marker,
                    anchor = paragraph.anchor
                )
            } else {
                pendingNested += paragraph
            }
        }
        flushNested()

        val firstMarker = paragraphs.first().listMarker!!
        val kind = if (firstMarker.ordered) ListKind.ORDERED else ListKind.UNORDERED
        return BlockNode.ListBlock(
            kind = kind,
            style = styleFor(firstMarker.numId),
            items = items,
            anchor = items.firstOrNull()?.anchor
        )
    }
}
