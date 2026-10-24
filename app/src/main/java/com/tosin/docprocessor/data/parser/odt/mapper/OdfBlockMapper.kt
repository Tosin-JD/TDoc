package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.ListMarkerFormatter
import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.IndexKind
import com.tosin.docprocessor.domain.document.model.ListMarker
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Element

/** Maps block-level ODF content, dispatching on namespace + local name. */
class OdfBlockMapper(
    private val context: OdfMapContext,
    private val inlineMapper: OdfInlineMapper,
    private val tableMapper: OdfTableMapper,
    private val frameMapper: OdfFrameMapper
) {

    fun parseBlocks(container: Element, depth: Int = 1): List<BlockNode> {
        val output = mutableListOf<BlockNode>()
        val children = container.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child !is Element) continue
            if (child.matchesNs(Ns.TEXT, "tracked-changes")) {
                output += revisionBlocks(child, depth)
                continue
            }
            parseBlock(child, depth)?.let { output += it }
        }
        return output
    }

    /** `text:tracked-changes` holds `text:insertion` / `text:deletion` blocks. */
    private fun revisionBlocks(container: Element, depth: Int): List<BlockNode> =
        container.childNodes.let { children ->
            (0 until children.length)
                .mapNotNull { children.item(it) as? Element }
                .filter { it.matchesNs(Ns.TEXT, "insertion") || it.matchesNs(Ns.TEXT, "deletion") }
                .map { revisionBlock(it, depth) }
        }

    private fun revisionBlock(element: Element, depth: Int): BlockNode.RevisionBlock {
        val info = element.firstNs(Ns.OFFICE, "change-info")
        val blocks = mutableListOf<BlockNode>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            // change-info metadata is consumed here, never mapped as content.
            if (child.matchesNs(Ns.OFFICE, "change-info")) continue
            parseBlock(child, depth + 1)?.let { blocks += it }
        }
        return BlockNode.RevisionBlock(
            kind = if (element.matchesNs(Ns.TEXT, "deletion")) {
                RevisionKind.DELETION
            } else RevisionKind.INSERTION,
            author = info?.firstNs(Ns.DC, "creator")?.textContent?.trim(),
            date = info?.firstNs(Ns.DC, "date")?.textContent?.trim(),
            blocks = blocks,
            anchor = SourceAnchor(context.part, "text:${element.localName}")
        )
    }

    private fun parseBlock(element: Element, depth: Int): BlockNode? = when {
        element.matchesNs(Ns.TEXT, "p") -> paragraph(element)
        element.matchesNs(Ns.TEXT, "h") -> heading(element)
        element.matchesNs(Ns.TEXT, "list") -> list(element, depth)
        element.matchesNs(Ns.TABLE, "table") -> tableMapper.parseTable(element)
        element.matchesNs(Ns.DRAW, "frame") -> frameMapper.parseBlockFrame(element)
        element.matchesNs(Ns.TEXT, "section") -> {
            val nested = parseBlocks(element, depth)
            nested.takeIf { it.isNotEmpty() }
                ?.let { BlockNode.TextBox(blocks = it, anchor = SourceAnchor(context.part, "text:section")) }
        }
        element.matchesNs(Ns.TEXT, "soft-page-break") ->
            BlockNode.PageBreak(SourceAnchor(context.part, "text:soft-page-break"))
        element.matchesNs(Ns.TEXT, "insertion") || element.matchesNs(Ns.TEXT, "deletion") ->
            revisionBlock(element, depth)
        element.matchesNs(Ns.TEXT, "table-of-content") || element.matchesNs(Ns.TEXT, "alphabetical-index") ||
            element.matchesNs(Ns.TEXT, "illustration-index") || element.matchesNs(Ns.TEXT, "table-index") ||
            element.matchesNs(Ns.TEXT, "object-index") || element.matchesNs(Ns.TEXT, "user-index") ||
            element.matchesNs(Ns.TEXT, "bibliography") -> indexBlock(element)
        else -> opaque(element)
    }

    private fun paragraph(element: Element): BlockNode {
        val style = context.styles.paragraphStyle(element.attrNs(Ns.TEXT, "style-name"))
        val baseRun = style.defaultRunStyle ?: TextStyle()
        return BlockNode.Paragraph(
            inlines = inlineMapper.parse(element, baseRun),
            style = style,
            listMarker = null,
            anchor = SourceAnchor(context.part, "text:p")
        )
    }

    private fun heading(element: Element): BlockNode {
        val style = context.styles.paragraphStyle(element.attrNs(Ns.TEXT, "style-name"))
        val level = (element.attrNs(Ns.TEXT, "outline-level")?.toIntOrNull() ?: 1).coerceIn(1, 6)
        val baseRun = style.defaultRunStyle ?: TextStyle()
        return BlockNode.Heading(
            inlines = inlineMapper.parse(element, baseRun),
            level = level,
            style = style.copy(outlineLevel = level),
            anchor = SourceAnchor(context.part, "text:h")
        )
    }

    private fun list(element: Element, depth: Int): BlockNode.ListBlock {
        val listStyle = context.styles.listStyle(element.attrNs(Ns.TEXT, "style-name"))
        val items = mutableListOf<BlockNode.ListItem>()
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.matchesNs(Ns.TEXT, "list-item")) {
                items += parseListItem(child, listStyle, depth)
            }
        }
        return BlockNode.ListBlock(
            kind = listStyle.kind,
            style = listStyle,
            items = items,
            anchor = SourceAnchor(context.part, "text:list")
        )
    }

    private fun parseListItem(
        item: Element,
        listStyle: com.tosin.docprocessor.domain.document.model.style.ListStyle,
        depth: Int
    ): BlockNode.ListItem {
        val blocks = parseBlocks(item, depth + 1)
        val level = listStyle.levels[depth] ?: listStyle.levels.values.firstOrNull()
        val ordered = listStyle.kind == ListKind.ORDERED
        val key = listStyle.styleId ?: listStyle.styleName ?: ODF_ANONYMOUS_LIST_KEY
        val marker = level?.let {
            val text = if (ordered) {
                val sequence = context.listNumbering.nextItem(key, depth - 1)
                val value = (it.start ?: 1) + (sequence - 1)
                ListMarkerFormatter.render(
                    template = it.levelText,
                    value = value,
                    format = ListMarkerFormatter.parseFormat(it.format),
                    levelNumber = depth
                )
            } else {
                it.bulletChar ?: it.levelText
            }
            ListMarker(
                text = text,
                level = depth - 1,
                ordered = ordered,
                numId = key
            )
        }
        return BlockNode.ListItem(blocks, marker, SourceAnchor(context.part, "text:list-item"))
    }

    private companion object {
        /** Key for anonymous lists so siblings still share (continue) numbering. */
        const val ODF_ANONYMOUS_LIST_KEY = "odf:anonymous-list"
    }

    private fun indexBlock(element: Element): BlockNode.IndexBlock {
        val kind = when {
            element.matchesNs(Ns.TEXT, "alphabetical-index") -> IndexKind.ALPHABETICAL
            element.matchesNs(Ns.TEXT, "illustration-index") -> IndexKind.ILLUSTRATION
            element.matchesNs(Ns.TEXT, "table-index") -> IndexKind.TABLE_INDEX
            element.matchesNs(Ns.TEXT, "object-index") -> IndexKind.OBJECT
            element.matchesNs(Ns.TEXT, "user-index") -> IndexKind.USER
            element.matchesNs(Ns.TEXT, "bibliography") -> IndexKind.BIBLIOGRAPHY
            else -> IndexKind.TABLE_OF_CONTENTS
        }
        return BlockNode.IndexBlock(kind, anchor = SourceAnchor(context.part, "text:index"))
    }

    private fun opaque(element: Element): BlockNode {
        val local = element.localName ?: element.nodeName
        context.diagnostics.preserve("${element.prefix ?: "odf"}:$local", "block")
        return BlockNode.Opaque(
            OpaqueFragmentCapture.capture(element, SourceAnchor(context.part, local), "unmapped block")
        )
    }
}
