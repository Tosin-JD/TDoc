package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.resolve.DocxRunProperties
import com.tosin.docprocessor.domain.document.model.BreakKind
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.NoteKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Element

/** Result of scanning a single `w:r` element. */
data class RunChildren(
    val nodes: List<InlineNode>,
    val instructionText: String = "",
    val fieldChar: String? = null
)

/** Maps `w:r` (and its `rPr`) into ordered inline nodes. */
class DocxRunMapper(private val context: DocxMapContext) {

    fun runStyle(run: Element, baseStyle: TextStyle): TextStyle {
        val rPr = run.wFirst("rPr")
        var style = baseStyle
        rPr?.wFirst("rStyle")?.wAttr("val")?.let { style = style.mergedWith(context.styles.characterStyle(it)) }
        style = style.mergedWith(DocxRunProperties.parse(rPr))
        return style
    }

    fun parseRun(run: Element, baseStyle: TextStyle): RunChildren {
        val style = runStyle(run, baseStyle)
        val nodes = mutableListOf<InlineNode>()
        val instruction = StringBuilder()
        var fieldChar: String? = null
        val anchor = SourceAnchor(context.part, "w:r")

        run.childNodes.let { children ->
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                when {
                    child.matchesNs(Ns.W, "t") || child.matchesNs(Ns.W, "delText") ->
                        if (child.textContent.isNotEmpty()) {
                            nodes += InlineNode.Text(child.textContent, style, anchor)
                        }
                    child.matchesNs(Ns.W, "tab") -> nodes += InlineNode.Tab(anchor)
                    child.matchesNs(Ns.W, "br") -> {
                        val kind = when (child.wAttr("type")?.lowercase()) {
                            "page" -> BreakKind.PAGE
                            "column" -> BreakKind.COLUMN
                            else -> BreakKind.LINE
                        }
                        nodes += InlineNode.Break(kind, anchor)
                    }
                    child.matchesNs(Ns.W, "cr") -> nodes += InlineNode.Break(BreakKind.CARRIAGE, anchor)
                    child.matchesNs(Ns.W, "noBreakHyphen") -> nodes += InlineNode.NoBreakHyphen(anchor)
                    child.matchesNs(Ns.W, "softHyphen") -> nodes += InlineNode.SoftHyphen(anchor)
                    child.matchesNs(Ns.W, "sym") -> nodes += InlineNode.Symbol(
                        characterCode = child.wAttr("char"),
                        font = child.wAttr("font"),
                        anchor = anchor
                    )
                    child.matchesNs(Ns.W, "lastRenderedPageBreak") ->
                        nodes += InlineNode.Break(BreakKind.CACHED_PAGE, anchor)
                    child.matchesNs(Ns.W, "instrText") -> instruction.append(child.textContent)
                    child.matchesNs(Ns.W, "fldChar") -> fieldChar = child.wAttr("fldCharType")
                    child.matchesNs(Ns.W, "footnoteReference") -> nodes += InlineNode.NoteReference(
                        NoteKind.FOOTNOTE,
                        child.wAttr("id").orEmpty(),
                        anchor
                    )
                    child.matchesNs(Ns.W, "endnoteReference") -> nodes += InlineNode.NoteReference(
                        NoteKind.ENDNOTE,
                        child.wAttr("id").orEmpty(),
                        anchor
                    )
                    child.matchesNs(Ns.W, "commentReference") -> nodes += InlineNode.CommentReference(
                        child.wAttr("id").orEmpty(),
                        anchor
                    )
                    child.matchesNs(Ns.W, "drawing") -> nodes += DocxDrawingMapper(context).parseDrawing(child)
                    child.matchesNs(Ns.W, "pict") -> nodes += DocxDrawingMapper(context).parseVmlPicture(child)
                    child.matchesNs(Ns.W, "object") -> {
                        context.diagnostics.preserve("w:object", "w:r")
                        nodes += InlineNode.Opaque(
                            com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture.capture(
                                child, anchor, "embedded object not rendered"
                            )
                        )
                    }
                    child.matchesNs(Ns.W, "ruby") -> nodes += parseRuby(child, style)
                    child.matchesNs(Ns.W, "annotationRef") -> Unit
                }
            }
        }

        return RunChildren(nodes = nodes, instructionText = instruction.toString(), fieldChar = fieldChar)
    }

    private fun parseRuby(ruby: Element, style: TextStyle): InlineNode {
        val base = mutableListOf<InlineNode>()
        val rubyText = mutableListOf<InlineNode>()
        ruby.w("rubyBase")?.firstOrNull()?.let { baseElement ->
            baseElement.w("r").forEach { run ->
                parseRun(run, style).nodes.forEach(base::add)
            }
        }
        ruby.w("rt")?.firstOrNull()?.let { rtElement ->
            rtElement.w("r").forEach { run ->
                parseRun(run, style).nodes.forEach(rubyText::add)
            }
        }
        return InlineNode.Ruby(base = base, rubyText = rubyText, anchor = SourceAnchor(context.part, "w:ruby"))
    }
}
