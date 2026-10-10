package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.CommentDefinition
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.NoteKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.UUID

/**
 * Recursive ODF inline mapper: preserves child order across text nodes, spans,
 * links, spaces, tabs, breaks, notes, annotations and frames.
 */
class OdfInlineMapper(
    private val context: OdfMapContext,
    private val frameMapper: OdfFrameMapper
) {

    lateinit var blockMapper: OdfBlockMapper
        private set

    private val notes = linkedMapOf<String, BlockNode>()
    private val comments = linkedMapOf<String, CommentDefinition>()

    fun attach(blockMapper: OdfBlockMapper) {
        this.blockMapper = blockMapper
    }

    fun collectedNotes(): Map<String, BlockNode> = notes.toMap()

    fun collectedComments(): Map<String, CommentDefinition> = comments.toMap()

    fun parse(container: Element, style: TextStyle): List<InlineNode> {
        val output = mutableListOf<InlineNode>()
        val children = container.childNodes
        for (index in 0 until children.length) {
            when (val node = children.item(index)) {
                is org.w3c.dom.Text -> if (node.nodeValue.isNotEmpty()) {
                    output += InlineNode.Text(node.nodeValue, style, SourceAnchor(context.part, "text()"))
                }
                is Element -> output += parseElement(node, style)
                else -> if (node.nodeType == Node.CDATA_SECTION_NODE && !node.nodeValue.isNullOrEmpty()) {
                    output += InlineNode.Text(node.nodeValue, style, SourceAnchor(context.part, "text()"))
                }
            }
        }
        return output
    }

    private fun parseElement(element: Element, style: TextStyle): List<InlineNode> {
        val anchor = SourceAnchor(context.part, element.nodeName)
        return when {
            element.matchesNs(Ns.TEXT, "span") -> {
                val childStyle = style.mergedWith(context.styles.textStyle(element.attrNs(Ns.TEXT, "style-name")))
                listOf(InlineNode.Span(parse(element, childStyle), childStyle, anchor))
            }
            element.matchesNs(Ns.TEXT, "a") -> listOf(
                InlineNode.Hyperlink(
                    children = parse(element, style),
                    target = element.attrNs(Ns.XLINK, "href"),
                    tooltip = element.attrNs(Ns.OFFICE, "name") ?: element.attrNs(Ns.OFFICE, "title"),
                    anchor = anchor
                )
            )
            element.matchesNs(Ns.TEXT, "s") -> {
                val count = element.attrNs(Ns.TEXT, "c")?.toIntOrNull() ?: 1
                listOf(InlineNode.Text(" ".repeat(count.coerceIn(1, 64)), style, anchor))
            }
            element.matchesNs(Ns.TEXT, "tab") -> listOf(InlineNode.Tab(anchor))
            element.matchesNs(Ns.TEXT, "line-break") -> listOf(InlineNode.Break(
                com.tosin.docprocessor.domain.document.model.BreakKind.LINE, anchor
            ))
            element.matchesNs(Ns.TEXT, "soft-page-break") -> listOf(InlineNode.Break(
                com.tosin.docprocessor.domain.document.model.BreakKind.SOFT_PAGE, anchor
            ))
            element.matchesNs(Ns.TEXT, "bookmark") -> listOf(
                InlineNode.BookmarkBoundary(
                    bookmarkId = element.attrNs(Ns.TEXT, "id") ?: UUID.randomUUID().toString(),
                    name = element.attrNs(Ns.TEXT, "name"),
                    isStart = true,
                    anchor = anchor
                )
            )
            element.matchesNs(Ns.TEXT, "bookmark-start") -> listOf(
                InlineNode.BookmarkBoundary(
                    bookmarkId = element.attrNs(Ns.TEXT, "id") ?: UUID.randomUUID().toString(),
                    name = element.attrNs(Ns.TEXT, "name"),
                    isStart = true,
                    anchor = anchor
                )
            )
            element.matchesNs(Ns.TEXT, "bookmark-end") -> listOf(
                InlineNode.BookmarkBoundary(
                    bookmarkId = element.attrNs(Ns.TEXT, "id") ?: UUID.randomUUID().toString(),
                    isStart = false,
                    anchor = anchor
                )
            )
            element.matchesNs(Ns.TEXT, "note") -> listOf(parseNote(element, style))
            element.matchesNs(Ns.OFFICE, "annotation") -> listOf(parseAnnotation(element))
            element.matchesNs(Ns.OFFICE, "annotation-end") -> listOf(
                InlineNode.CommentBoundary(
                    commentId = element.attrNs(Ns.OFFICE, "name") ?: UUID.randomUUID().toString(),
                    isStart = false,
                    anchor = anchor
                )
            )
            element.matchesNs(Ns.DRAW, "frame") -> listOf(frameMapper.parseInlineFrame(element))
            element.matchesNs(Ns.TEXT, "change") || element.matchesNs(Ns.TEXT, "change-start") ||
                element.matchesNs(Ns.TEXT, "change-end") -> {
                context.diagnostics.preserve("text:${element.localName}", "inline")
                listOf(
                    InlineNode.Opaque(
                        com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture.capture(
                            element, anchor, "tracked change not rendered"
                        )
                    )
                )
            }
            element.matchesNs(Ns.TEXT, "number") ->
                listOf(InlineNode.Text(element.textContent.orEmpty(), style, anchor))
            element.matchesNs(Ns.TEXT, "note-citation") -> emptyList()
            else -> parse(element, style)
        }
    }

    private fun parseNote(element: Element, style: TextStyle): InlineNode {
        val id = element.attrNs(Ns.TEXT, "id") ?: UUID.randomUUID().toString()
        val kind = if (element.attrNs(Ns.TEXT, "note-class") == "endnote") NoteKind.ENDNOTE else NoteKind.FOOTNOTE
        val body = element.firstNs(Ns.TEXT, "note-body")
        if (body != null && ::blockMapper.isInitialized) {
            val blocks = blockMapper.parseBlocks(body)
            notes[id] = BlockNode.NoteBody(kind, id, blocks, SourceAnchor(context.part, "text:note-body"))
        } else {
            context.diagnostics.preserve("text:note", "inline")
        }
        return InlineNode.NoteReference(kind, id, SourceAnchor(context.part, "text:note"))
    }

    private fun parseAnnotation(element: Element): InlineNode {
        val id = element.attrNs(Ns.OFFICE, "name") ?: UUID.randomUUID().toString()
        val blocks = if (::blockMapper.isInitialized) blockMapper.parseBlocks(element) else emptyList()
        comments[id] = CommentDefinition(
            id = id,
            author = element.firstNs(Ns.DC, "creator")?.textContent?.trim(),
            initials = element.firstNs(Ns.META, "creator-initial")?.textContent?.trim(),
            date = element.firstNs(Ns.DC, "date")?.textContent?.trim(),
            text = element.elementsNs(Ns.TEXT, "p").joinToString("\n") { it.textContent.orEmpty().trim() },
            blocks = blocks
        )
        return InlineNode.CommentReference(id, SourceAnchor(context.part, "office:annotation"))
    }
}
