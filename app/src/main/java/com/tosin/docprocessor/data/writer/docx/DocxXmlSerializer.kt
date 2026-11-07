package com.tosin.docprocessor.data.writer.docx

import com.tosin.docprocessor.data.writer.common.XmlTextSanitizer
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import java.util.concurrent.atomic.AtomicInteger

internal class DocxXmlSerializer {
    private val drawingIds = AtomicInteger()

    fun document(document: ParsedDocument, imageRelationshipIds: Map<String, String>): String {
        val content = document.blocks.joinToString("") { block(it, imageRelationshipIds) }
        val section = document.sections.lastOrNull()?.let { style ->
            buildString {
                append("<w:sectPr>")
                style.page.widthPoints?.let { append("<w:pgSz w:w=\"${twips(it)}\"") }
                if (style.page.widthPoints != null) {
                    style.page.heightPoints?.let { append(" w:h=\"${twips(it)}\"") }
                    append("/>")
                } else if (style.page.heightPoints != null) {
                    append("<w:pgSz w:h=\"${twips(style.page.heightPoints)}\"/>")
                }
                append("</w:sectPr>")
            }
        } ?: "<w:sectPr/>"
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>$content$section</w:body>
            </w:document>""".trimIndent()
        return xml
    }

    fun styles(document: ParsedDocument): String {
        val styles = document.styles.paragraphStyles.values
        val customStyles = styles.mapNotNull { style ->
            val id = style.styleId ?: return@mapNotNull null
            """<w:style w:type="paragraph" w:styleId="${attr(id)}"><w:name w:val="${attr(style.styleName ?: id)}"/></w:style>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
              <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:pPr><w:outlineLvl w:val="1"/></w:pPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:pPr><w:outlineLvl w:val="2"/></w:pPr></w:style>
              ${((4..9).joinToString("") { level -> """<w:style w:type="paragraph" w:styleId="Heading$level"><w:name w:val="heading $level"/><w:pPr><w:outlineLvl w:val="${level - 1}"/></w:pPr></w:style>""" })}
              $customStyles
            </w:styles>""".trimIndent()
    }

    fun numbering(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/>
            <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
          </w:abstractNum>
          <w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="singleLevel"/>
            <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
          </w:abstractNum>
          <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
        </w:numbering>"""

    private fun block(node: BlockNode, imageIds: Map<String, String>): String = when (node) {
        is BlockNode.Paragraph -> paragraph(node.inlines, node.style.styleId, node.listMarker?.ordered, node.style.pageBreakBefore, imageIds)
        is BlockNode.Heading -> paragraph(node.inlines, "Heading${node.level.coerceIn(1, 9)}", null, node.style.pageBreakBefore, imageIds)
        is BlockNode.ListBlock -> node.items.joinToString("") { item ->
            item.blocks.joinToString("") { child ->
                when (child) {
                    is BlockNode.Paragraph -> paragraph(child.inlines, child.style.styleId, node.kind != com.tosin.docprocessor.domain.document.model.style.ListKind.UNORDERED, false, imageIds)
                    else -> block(child, imageIds)
                }
            }
        }
        is BlockNode.ListItem -> node.blocks.joinToString("") { block(it, imageIds) }
        is BlockNode.Table -> """<w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/></w:tblPr>${node.rows.joinToString("") { row -> "<w:tr>${row.cells.joinToString("") { cell -> "<w:tc>${cell.blocks.joinToString("") { block(it, imageIds) }.ifBlank { "<w:p/>" }}</w:tc>" }}</w:tr>" }}</w:tbl>"""
        is BlockNode.TableRow, is BlockNode.TableCell -> ""
        is BlockNode.Figure -> "<w:p>${image(node.resourceId, node.altText, "", imageIds)}${node.caption.joinToString("") { inline(it, imageIds) }}</w:p>"
        is BlockNode.PageBreak -> "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
        is BlockNode.SectionBreak -> "<w:p><w:pPr><w:sectPr/></w:pPr></w:p>"
        is BlockNode.TextBox -> node.blocks.joinToString("") { block(it, imageIds) }
        is BlockNode.IndexBlock -> paragraph(listOf(InlineNode.Text(node.title ?: node.kind.name)), null, null, false, imageIds)
        is BlockNode.NoteBody -> node.blocks.joinToString("") { block(it, imageIds) }
        is BlockNode.RevisionBlock -> node.blocks.joinToString("") { block(it, imageIds) }
        is BlockNode.Opaque -> throw IllegalArgumentException("Cannot export opaque block ${node.node.localName}: ${node.node.reason.orEmpty()}")
    }

    private fun paragraph(
        inlines: List<InlineNode>,
        styleId: String?,
        ordered: Boolean?,
        pageBreakBefore: Boolean?,
        imageIds: Map<String, String>
    ): String {
        val props = buildString {
            styleId?.let { append("<w:pStyle w:val=\"${attr(it)}\"/>") }
            if (pageBreakBefore == true) append("<w:pageBreakBefore/>")
            if (ordered != null) append("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"${if (ordered) 1 else 2}\"/></w:numPr>")
        }.takeIf { it.isNotEmpty() }?.let { "<w:pPr>$it</w:pPr>" }.orEmpty()
        return "<w:p>$props${inlines.joinToString("") { inline(it, imageIds) }}</w:p>"
    }

    private fun inline(node: InlineNode, imageIds: Map<String, String>, style: TextStyle = TextStyle()): String = when (node) {
        is InlineNode.Text -> run(node.text, style.mergedWith(node.style))
        is InlineNode.Span -> node.children.joinToString("") { inline(it, imageIds, style.mergedWith(node.style)) }
        is InlineNode.Break -> if (node.kind == com.tosin.docprocessor.domain.document.model.BreakKind.PAGE) "<w:r><w:br w:type=\"page\"/></w:r>" else "<w:r><w:br/></w:r>"
        is InlineNode.Tab -> "<w:r><w:tab/></w:r>"
        is InlineNode.NoBreakHyphen -> run("\u2011", style)
        is InlineNode.SoftHyphen -> run("\u00AD", style)
        is InlineNode.Symbol -> node.characterCode?.let { run(it, style) }.orEmpty()
        is InlineNode.Hyperlink -> node.children.joinToString("") { inline(it, imageIds, style) }
        is InlineNode.Revision, is InlineNode.ContentControl -> when (node) {
            is InlineNode.Revision -> node.children.joinToString("") { inline(it, imageIds, style) }
            is InlineNode.ContentControl -> node.children.joinToString("") { inline(it, imageIds, style) }
            else -> ""
        }
        is InlineNode.Field -> run(node.result.joinToString("") { inline(it, imageIds, style) }, style)
        is InlineNode.Ruby -> node.base.joinToString("") { inline(it, imageIds, style) }
        is InlineNode.TextBox -> node.blocks.joinToString("") { block(it, imageIds) }
        is InlineNode.Drawing -> image(node.resourceId, node.altText, node.title.orEmpty(), imageIds)
        is InlineNode.BookmarkBoundary, is InlineNode.NoteReference, is InlineNode.CommentBoundary,
        is InlineNode.CommentReference -> ""
        is InlineNode.Opaque -> throw IllegalArgumentException("Cannot export opaque inline content ${node.node.localName}: ${node.node.reason.orEmpty()}")
    }

    private fun run(text: String, style: TextStyle): String {
        val properties = buildString {
            if (style.bold == true) append("<w:b/>")
            if (style.italic == true) append("<w:i/>")
            style.underline?.takeIf { it != com.tosin.docprocessor.domain.document.model.style.UnderlineKind.NONE }?.let { append("<w:u w:val=\"single\"/>") }
            if (style.strike == true) append("<w:strike/>")
            style.color?.let { append("<w:color w:val=\"${attr(it.removePrefix("#"))}\"/>") }
            style.fontSizePoints?.let { append("<w:sz w:val=\"${(it * 2).toInt().coerceAtLeast(1)}\"/>") }
            style.fontAscii?.let { append("<w:rFonts w:ascii=\"${attr(it)}\" w:hAnsi=\"${attr(style.fontHAnsi ?: it)}\"/>") }
            if (style.verticalAlign != null) append("<w:vertAlign w:val=\"${style.verticalAlign.name.lowercase()}\"/>")
        }.takeIf { it.isNotEmpty() }?.let { "<w:rPr>$it</w:rPr>" }.orEmpty()
        val preserve = if (text.firstOrNull()?.isWhitespace() == true || text.lastOrNull()?.isWhitespace() == true) " xml:space=\"preserve\"" else ""
        return "<w:r>$properties<w:t$preserve>${XmlTextSanitizer.escapeText(text)}</w:t></w:r>"
    }

    private fun image(resourceId: String?, alt: String?, caption: String, imageIds: Map<String, String>): String {
        val relationshipId = resourceId?.let(imageIds::get)
        if (relationshipId == null) return caption.takeIf { it.isNotBlank() }?.let { run(it, TextStyle()) }.orEmpty()
        val drawingId = drawingIds.incrementAndGet()
        return """<w:r><w:drawing><wp:inline><wp:extent cx="952500" cy="635000"/><wp:docPr id="$drawingId" name="Image" descr="${attr(alt.orEmpty())}"/>
            <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>
            <pic:nvPicPr><pic:cNvPr id="$drawingId" name="image"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relationshipId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
            <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="952500" cy="635000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
            </pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
    }

    private fun twips(points: Double) = (points * 20).toInt().coerceAtLeast(1)
    private fun attr(value: String) = XmlTextSanitizer.escapeAttribute(value)
}
