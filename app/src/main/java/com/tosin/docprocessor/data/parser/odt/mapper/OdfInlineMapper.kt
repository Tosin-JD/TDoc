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
import com.tosin.docprocessor.domain.document.model.RevisionKind
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
        // Open tracked-change range (`text:change-start` … `text:change-end`).
        var changeBuffer: MutableList<InlineNode>? = null
        val children = container.childNodes
        for (index in 0 until children.length) {
            when (val node = children.item(index)) {
                is org.w3c.dom.Text -> if (node.nodeValue.isNotEmpty()) {
                    val text = InlineNode.Text(node.nodeValue, style, SourceAnchor(context.part, "text()"))
                    if (changeBuffer != null) changeBuffer += text else output += text
                }
                is Element -> when {
                    node.matchesNs(Ns.TEXT, "change-start") -> changeBuffer = mutableListOf()
                    node.matchesNs(Ns.TEXT, "change-end") -> {
                        val buffered = changeBuffer
                        changeBuffer = null
                        output += InlineNode.Revision(
                            kind = RevisionKind.INSERTION,
                            children = buffered.orEmpty(),
                            anchor = SourceAnchor(context.part, "text:change-end")
                        )
                    }
                    else -> {
                        val parsed = parseElement(node, style)
                        if (changeBuffer != null) changeBuffer?.addAll(parsed) else output += parsed
                    }
                }
                else -> if (node.nodeType == Node.CDATA_SECTION_NODE && !node.nodeValue.isNullOrEmpty()) {
                    val text = InlineNode.Text(node.nodeValue.orEmpty(), style, SourceAnchor(context.part, "text()"))
                    if (changeBuffer != null) changeBuffer += text else output += text
                }
            }
        }
        // Unmatched start: keep the partial content visible inside a revision.
        changeBuffer?.let { buffered ->
            if (buffered.isNotEmpty()) {
                output += InlineNode.Revision(kind = RevisionKind.INSERTION, children = buffered)
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
            element.matchesNs(Ns.TEXT, "change") -> {
                // Standalone marker; the change payload lives in tracked-changes.
                val cached = element.textContent?.trim().orEmpty()
                listOf(
                    InlineNode.Revision(
                        kind = RevisionKind.INSERTION,
                        children = listOfNotNull(
                            cached.takeIf { it.isNotEmpty() }?.let { InlineNode.Text(it, style, anchor) }
                        ),
                        anchor = anchor
                    )
                )
            }
            element.matchesNs(Ns.TEXT, "number") ->
                listOf(InlineNode.Text(element.textContent.orEmpty(), style, anchor))
            element.matchesNs(Ns.TEXT, "note-citation") -> emptyList()
            element.namespaceURI == Ns.TEXT && FIELD_KINDS.containsKey(element.localName) ->
                listOf(textField(element, style, anchor))
            else -> parse(element, style)
        }
    }

    /**
     * ODF text fields (`text:page-number`, `text:ref`, `text:sequence`, …)
     * become [InlineNode.Field] nodes with the cached display text as `result`
     * — the same shape DOCX fields take, so header/footer stories keep their
     * page numbers instead of silently losing them.
     */
    private fun textField(element: Element, style: TextStyle, anchor: SourceAnchor): InlineNode {
        val local = element.localName.orEmpty()
        val reference = element.attrNs(Ns.TEXT, "ref-name") ?: element.attrNs(Ns.TEXT, "name")
        val cached = element.textContent?.trim().orEmpty()
        return InlineNode.Field(
            kind = FIELD_KINDS[local] ?: local.uppercase(),
            instruction = listOfNotNull("text:$local", reference).joinToString(separator = " "),
            result = listOfNotNull(
                cached.takeIf { it.isNotEmpty() }?.let { InlineNode.Text(it, style, anchor) }
            ),
            isSimple = true,
            anchor = anchor
        )
    }

    private companion object {
        /** ODF field element local name → field kind (aligned with DOCX names). */
        val FIELD_KINDS = mapOf(
            "page-number" to "PAGE",
            "page-count" to "NUMPAGES",
            "file-name" to "FILENAME",
            "file-extension" to "FILEEXT",
            "template-name" to "TEMPLATE",
            "sheet-name" to "SHEETNAME",
            "chapter" to "CHAPTER",
            "sequence" to "SEQUENCE",
            "sequence-set" to "SEQUENCE",
            "ref" to "REF",
            "date" to "DATE",
            "time" to "TIME",
            "title" to "TITLE",
            "subject" to "SUBJECT",
            "description" to "DESCRIPTION",
            "keywords" to "KEYWORDS",
            "initial-creator" to "AUTHOR",
            "creator" to "LASTMODIFIEDBY",
            "editing-cycles" to "REVISION",
            "printing-name" to "PRINTING"
        )
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
