package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.resolve.DocxParagraphProperties
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ListMarker
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Element

/** Maps `w:p` (and `w:pPr`) into [BlockNode.Paragraph] or [BlockNode.Heading]. */
class DocxParagraphMapper(private val context: DocxMapContext) {

    private val runMapper = DocxRunMapper(context)

    /** Threaded from [DocxBlockMapper] so runs can map shapes with text payload. */
    fun attach(blockMapper: DocxBlockMapper) {
        runMapper.attach(blockMapper)
    }

    fun parseParagraph(paragraph: Element): BlockNode {
        val pPr = paragraph.wFirst("pPr")
        val direct = DocxParagraphProperties.parse(pPr)
        val resolvedStyle = context.styles.paragraphStyle(direct.styleId).mergedWith(direct)
        val baseRunStyle = resolvedStyle.defaultRunStyle ?: TextStyle()
        val inlines = parseInlineContainer(paragraph, baseRunStyle)
        val revisionWrapped = formatChangeWrap(pPr, inlines)

        val headingLevel = headingLevel(direct, resolvedStyle)
        if (headingLevel != null && inlines.isNotEmpty()) {
            return BlockNode.Heading(
                inlines = revisionWrapped,
                level = headingLevel,
                style = resolvedStyle,
                anchor = SourceAnchor(context.part, "w:p")
            )
        }

        val listMarker = parseListMarker(pPr)
        return BlockNode.Paragraph(
            inlines = revisionWrapped,
            style = resolvedStyle,
            listMarker = listMarker,
            anchor = SourceAnchor(context.part, "w:p")
        )
    }

    /** `w:pPrChange` marks a paragraph-format revision; keep the current
     *  formatting visible inside a FORMAT_CHANGE revision node. */
    private fun formatChangeWrap(pPr: Element?, inlines: List<InlineNode>): List<InlineNode> {
        val change = pPr?.wFirst("pPrChange") ?: return inlines
        context.diagnostics.warning(
            "w:pPrChange",
            "Current paragraph formatting retained; old paragraph-property snapshot was not interpreted"
        )
        return listOf(
            InlineNode.Revision(
                kind = RevisionKind.FORMAT_CHANGE,
                author = change.wAttr("author"),
                date = change.wAttr("date"),
                children = inlines,
                anchor = SourceAnchor(context.part, "w:pPrChange")
            )
        )
    }

    /** Parses mixed inline content (runs, hyperlinks, fields, revisions, SDTs). */
    fun parseInlineContainer(container: Element, baseRunStyle: TextStyle): List<InlineNode> {
        val inlines = mutableListOf<InlineNode>()
        var instruction: StringBuilder? = null
        var result: MutableList<InlineNode>? = null
        var inResult = false

        val children = container.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when {
                child.matchesNs(Ns.W, "r") -> {
                    val rc = runMapper.parseRun(child, baseRunStyle)
                    when (rc.fieldChar) {
                        "begin" -> {
                            if (instruction == null) {
                                instruction = StringBuilder()
                                result = mutableListOf()
                                inResult = false
                            }
                        }
                        "separate" -> inResult = true
                    }
                    if (rc.instructionText.isNotEmpty() && instruction != null) {
                        instruction.append(rc.instructionText)
                    }
                    if (rc.nodes.isNotEmpty()) {
                        if (instruction != null && inResult && result != null) {
                            result.addAll(rc.nodes)
                        } else if (instruction == null) {
                            inlines.addAll(rc.nodes)
                        } else {
                            result?.addAll(rc.nodes)
                        }
                    }
                    if (rc.fieldChar == "end" && instruction != null) {
                        val instr = instruction.toString()
                        inlines += InlineNode.Field(
                            kind = instr.substringBefore(' ').trim().ifBlank { null },
                            instruction = instr.trim(),
                            result = result?.toList().orEmpty(),
                            isSimple = false,
                            anchor = SourceAnchor(context.part, "w:fldChar")
                        )
                        instruction = null
                        result = null
                        inResult = false
                    }
                }
                child.matchesNs(Ns.W, "hyperlink") -> {
                    val relId = child.wAttr("id") ?: child.getAttributeNS(Ns.R, "id")
                    val relationship = relId?.takeIf { it.isNotBlank() }?.let { context.relationships()[it] }
                    inlines += InlineNode.Hyperlink(
                        children = parseInlineContainer(child, baseRunStyle),
                        target = relationship?.target?.takeIf { relationship.isExternal },
                        anchorName = child.wAttr("anchor")?.takeIf { it.isNotBlank() },
                        tooltip = child.wAttr("tooltip")?.takeIf { it.isNotBlank() },
                        docLocation = child.wAttr("docLocation")?.takeIf { it.isNotBlank() },
                        anchor = SourceAnchor(context.part, "w:hyperlink", relationshipId = relId)
                    )
                }
                child.matchesNs(Ns.W, "bookmarkStart") -> inlines += InlineNode.BookmarkBoundary(
                    bookmarkId = child.wAttr("id").orEmpty(),
                    name = child.wAttr("name"),
                    isStart = true,
                    anchor = SourceAnchor(context.part, "w:bookmarkStart")
                )
                child.matchesNs(Ns.W, "bookmarkEnd") -> inlines += InlineNode.BookmarkBoundary(
                    bookmarkId = child.wAttr("id").orEmpty(),
                    isStart = false,
                    anchor = SourceAnchor(context.part, "w:bookmarkEnd")
                )
                child.matchesNs(Ns.W, "commentRangeStart") -> inlines += InlineNode.CommentBoundary(
                    commentId = child.wAttr("id").orEmpty(),
                    isStart = true,
                    anchor = SourceAnchor(context.part, "w:commentRangeStart")
                )
                child.matchesNs(Ns.W, "commentRangeEnd") -> inlines += InlineNode.CommentBoundary(
                    commentId = child.wAttr("id").orEmpty(),
                    isStart = false,
                    anchor = SourceAnchor(context.part, "w:commentRangeEnd")
                )
                child.matchesNs(Ns.W, "fldSimple") -> {
                    val instr = child.wAttr("instr").orEmpty()
                    inlines += InlineNode.Field(
                        kind = instr.substringBefore(' ').trim().ifBlank { null },
                        instruction = instr.trim(),
                        result = parseInlineContainer(child, baseRunStyle),
                        isSimple = true,
                        anchor = SourceAnchor(context.part, "w:fldSimple")
                    )
                }
                child.matchesNs(Ns.W, "ins") -> inlines += InlineNode.Revision(
                    kind = RevisionKind.INSERTION,
                    author = child.wAttr("author"),
                    date = child.wAttr("date"),
                    children = parseInlineContainer(child, baseRunStyle),
                    anchor = SourceAnchor(context.part, "w:ins")
                )
                child.matchesNs(Ns.W, "del") -> inlines += InlineNode.Revision(
                    kind = RevisionKind.DELETION,
                    author = child.wAttr("author"),
                    date = child.wAttr("date"),
                    children = parseInlineContainer(child, baseRunStyle),
                    anchor = SourceAnchor(context.part, "w:del")
                )
                child.matchesNs(Ns.W, "moveFrom") -> inlines += InlineNode.Revision(
                    kind = RevisionKind.MOVE_FROM,
                    children = parseInlineContainer(child, baseRunStyle),
                    anchor = SourceAnchor(context.part, "w:moveFrom")
                )
                child.matchesNs(Ns.W, "moveTo") -> inlines += InlineNode.Revision(
                    kind = RevisionKind.MOVE_TO,
                    children = parseInlineContainer(child, baseRunStyle),
                    anchor = SourceAnchor(context.part, "w:moveTo")
                )
                child.matchesNs(Ns.W, "sdt") -> inlines += parseSdt(child, baseRunStyle)
                child.matchesNs(Ns.W, "smartTag") || child.matchesNs(Ns.W, "customXml") ->
                    inlines += parseInlineContainer(child, baseRunStyle)
                child.matchesNs(Ns.W, "proofErr") -> Unit
                else -> Unit
            }
        }
        return inlines
    }

    private fun parseSdt(sdt: Element, baseRunStyle: TextStyle): InlineNode {
        val sdtPr = sdt.wFirst("sdtPr")
        val content = sdt.wFirst("sdtContent")
        return InlineNode.ContentControl(
            tag = sdtPr?.wFirst("tag")?.wAttr("val"),
            alias = sdtPr?.wFirst("alias")?.wAttr("val"),
            binding = sdtPr?.wFirst("dataBinding")?.wAttr("xpath")?.takeIf { it.isNotBlank() },
            children = content?.let { parseInlineContainer(it, baseRunStyle) }.orEmpty(),
            anchor = SourceAnchor(context.part, "w:sdt")
        )
    }

    private fun parseListMarker(pPr: Element?): ListMarker? {
        val numPr = pPr?.wFirst("numPr") ?: return null
        val numId = numPr.wFirst("numId")?.wAttr("val") ?: return null
        val ilvl = numPr.wFirst("ilvl")?.wAttr("val")?.toIntOrNull() ?: 0
        val ordered = context.numbering.isOrdered(numId, ilvl)
        val sequence = context.listNumbering.nextItem(numId, ilvl)
        return ListMarker(
            text = context.numbering.marker(numId, ilvl, sequence),
            level = ilvl,
            ordered = ordered,
            numId = numId
        )
    }

    private fun headingLevel(direct: ParagraphStyle, resolved: ParagraphStyle): Int? {
        val name = (resolved.styleName ?: direct.styleName ?: resolved.styleId ?: direct.styleId)?.lowercase()
            ?: return (direct.outlineLevel ?: resolved.outlineLevel)?.plus(1)
        Regex("heading\\s*(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        val outline = direct.outlineLevel ?: resolved.outlineLevel
        return outline?.plus(1)
    }
}
