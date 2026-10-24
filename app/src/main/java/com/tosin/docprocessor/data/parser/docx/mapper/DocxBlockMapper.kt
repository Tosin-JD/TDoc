package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture.capture
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.resolve.DocxSectionProperties
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.RevisionKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import org.w3c.dom.Element

/**
 * Maps a block-level container (body, table cell, SDT content) into an ordered
 * list of [BlockNode]s. Owns paragraph/table/revision/SDT block dispatch.
 */
class DocxBlockMapper(private val context: DocxMapContext) {

    private val paragraphMapper = DocxParagraphMapper(context).also { it.attach(this) }
    lateinit var tableMapper: DocxTableMapper
        private set

    fun attach(tableMapper: DocxTableMapper) {
        this.tableMapper = tableMapper
    }

    /** Number of section breaks emitted so far (body paragraphs carrying `sectPr`). */
    var sectionBreakCount = 0
        private set

    fun parseBlocks(container: Element): List<BlockNode> {
        val blocks = mutableListOf<BlockNode>()
        val children = container.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when {
                child.matchesNs(Ns.W, "p") -> {
                    blocks += paragraphMapper.parseParagraph(child)
                    // Paragraph-level section properties close the current section.
                    child.wFirst("pPr")?.wFirst("sectPr")?.let { sectPr ->
                        blocks += BlockNode.SectionBreak(
                            style = DocxSectionProperties.parse(
                                sectPr, sectionBreakCount + 1, context.evenAndOddHeaders
                            ),
                            anchor = SourceAnchor(context.part, "w:sectPr")
                        )
                        sectionBreakCount++
                    }
                }
                child.matchesNs(Ns.W, "tbl") -> blocks += tableMapper.parseTable(child)
                child.matchesNs(Ns.W, "sdt") -> {
                    val content = child.wFirst("sdtContent")
                    if (content != null) blocks += parseBlocks(content)
                    else context.diagnostics.preserve("w:sdt", "block")
                }
                child.matchesNs(Ns.W, "ins") -> blocks += BlockNode.RevisionBlock(
                    kind = RevisionKind.INSERTION,
                    author = child.getAttributeNS(Ns.W, "author").takeIf { it.isNotBlank() },
                    date = child.getAttributeNS(Ns.W, "date").takeIf { it.isNotBlank() },
                    blocks = parseBlocks(child),
                    anchor = SourceAnchor(context.part, "w:ins")
                )
                child.matchesNs(Ns.W, "del") -> blocks += BlockNode.RevisionBlock(
                    kind = RevisionKind.DELETION,
                    author = child.getAttributeNS(Ns.W, "author").takeIf { it.isNotBlank() },
                    date = child.getAttributeNS(Ns.W, "date").takeIf { it.isNotBlank() },
                    blocks = parseBlocks(child),
                    anchor = SourceAnchor(context.part, "w:del")
                )
                child.matchesNs(Ns.W, "moveFrom") -> blocks += BlockNode.RevisionBlock(
                    kind = RevisionKind.MOVE_FROM,
                    author = child.getAttributeNS(Ns.W, "author").takeIf { it.isNotBlank() },
                    date = child.getAttributeNS(Ns.W, "date").takeIf { it.isNotBlank() },
                    blocks = parseBlocks(child),
                    anchor = SourceAnchor(context.part, "w:moveFrom")
                )
                child.matchesNs(Ns.W, "moveTo") -> blocks += BlockNode.RevisionBlock(
                    kind = RevisionKind.MOVE_TO,
                    author = child.getAttributeNS(Ns.W, "author").takeIf { it.isNotBlank() },
                    date = child.getAttributeNS(Ns.W, "date").takeIf { it.isNotBlank() },
                    blocks = parseBlocks(child),
                    anchor = SourceAnchor(context.part, "w:moveTo")
                )
                child.matchesNs(Ns.W, "sectPr") -> Unit // handled by the document mapper
                child.matchesNs(Ns.W, "altChunk") -> {
                    context.diagnostics.preserve("w:altChunk", "body")
                    blocks += BlockNode.Opaque(
                        capture(
                            child,
                            SourceAnchor(context.part, "w:altChunk"),
                            "altChunk merge not supported; content preserved"
                        )
                    )
                }
                child.matchesNs(Ns.W, "bookmarkStart") || child.matchesNs(Ns.W, "bookmarkEnd") ||
                    child.matchesNs(Ns.W, "commentRangeStart") || child.matchesNs(Ns.W, "commentRangeEnd") ||
                    child.matchesNs(Ns.W, "proofErr") -> Unit
                else -> {
                    context.diagnostics.preserve("w:${child.localName ?: child.nodeName}", "body")
                    blocks += BlockNode.Opaque(
                        capture(child, SourceAnchor(context.part, "w:${child.localName}"), "unknown block")
                    )
                }
            }
        }
        return blocks
    }
}
