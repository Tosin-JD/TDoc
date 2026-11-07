package com.tosin.docprocessor.data.writer.odt

import com.tosin.docprocessor.data.writer.common.XmlTextSanitizer
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.style.TextStyle

internal class OdtXmlSerializer {
    private val textStyles = linkedMapOf<TextStyle, String>()

    fun content(document: ParsedDocument, imagePaths: Map<String, String>): String {
        textStyles.clear()
        val body = document.blocks.joinToString("") { block(it, imagePaths) }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="${OdtPackageConstants.OFFICE_NS}"
              xmlns:text="${OdtPackageConstants.TEXT_NS}"
              xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
              xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
              xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
              xmlns:xlink="http://www.w3.org/1999/xlink"
              xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
              office:version="1.2"><office:automatic-styles/>
              <office:body><office:text>$body</office:text></office:body>
            </office:document-content>""".trimIndent()
    }

    fun styles(): String {
        val definitions = textStyles.entries.joinToString("") { (style, name) ->
            val props = buildString {
                style.fontSizePoints?.let { append(""" fo:font-size="${decimal(it)}pt"""") }
                style.color?.let { append(""" fo:color="#${attr(it.removePrefix("#"))}"""") }
                if (style.bold == true) append(""" fo:font-weight="bold"""")
                if (style.italic == true) append(""" fo:font-style="italic"""")
                if (style.underline != null && style.underline != com.tosin.docprocessor.domain.document.model.style.UnderlineKind.NONE) {
                    append(""" style:text-underline-style="solid"""")
                }
                if (style.strike == true) append(""" style:text-line-through-style="solid"""")
                style.fontAscii?.let { append(""" fo:font-family="${attr(it)}"""") }
            }
            """<style:style style:name="$name" style:family="text"><style:text-properties$props/></style:style>"""
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
            <office:document-styles xmlns:office="${OdtPackageConstants.OFFICE_NS}"
              xmlns:style="${OdtPackageConstants.STYLE_NS}" xmlns:text="${OdtPackageConstants.TEXT_NS}"
              xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.2">
              <office:styles>$definitions
                <text:list-style style:name="L1"><text:list-level-style-number text:level="1" style:num-format="1" text:style-name="ListLabel"><style:list-level-properties text:space-before="0.5in" text:min-label-width="0.25in"/></text:list-level-style-number></text:list-style>
                <text:list-style style:name="L2"><text:list-level-style-bullet text:level="1" text:bullet-char="•"><style:list-level-properties text:space-before="0.5in" text:min-label-width="0.25in"/></text:list-level-style-bullet></text:list-style>
                <style:style style:name="TDocPageBreak" style:family="paragraph"><style:paragraph-properties fo:break-before="page"/></style:style>
              </office:styles><office:automatic-styles/>
              <office:master-styles/>
            </office:document-styles>""".trimIndent()
    }

    fun metadata(document: ParsedDocument): String {
        val meta = document.metadata
        return """<?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta xmlns:office="${OdtPackageConstants.OFFICE_NS}"
              xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
              xmlns:dc="http://purl.org/dc/elements/1.1/" office:version="1.2"><office:meta>
              <meta:generator>TDoc</meta:generator>
              ${meta.title?.let { "<dc:title>${text(it)}</dc:title>" }.orEmpty()}
              ${meta.subject?.let { "<dc:subject>${text(it)}</dc:subject>" }.orEmpty()}
              ${meta.description?.let { "<dc:description>${text(it)}</dc:description>" }.orEmpty()}
              ${meta.creator?.let { "<dc:creator>${text(it)}</dc:creator>" }.orEmpty()}
              ${meta.keywords.joinToString("") { "<meta:keyword>${text(it)}</meta:keyword>" }}
            </office:meta></office:document-meta>""".trimIndent()
    }

    private fun block(node: BlockNode, imagePaths: Map<String, String>): String = when (node) {
        is BlockNode.Paragraph -> {
            val paragraph = "<text:p>${node.inlines.joinToString("") { inline(it, imagePaths) }}</text:p>"
            node.listMarker?.let { marker ->
                val style = if (marker.ordered) "L1" else "L2"
                """<text:list text:style-name="$style"><text:list-item>$paragraph</text:list-item></text:list>"""
            } ?: paragraph
        }
        is BlockNode.Heading -> """<text:h text:outline-level="${node.level.coerceIn(1, 10)}">${node.inlines.joinToString("") { inline(it, imagePaths) }}</text:h>"""
        is BlockNode.ListBlock -> {
            val listStyle = if (node.kind == com.tosin.docprocessor.domain.document.model.style.ListKind.ORDERED) "L1" else "L2"
            """<text:list text:style-name="$listStyle">${node.items.joinToString("") { item ->
                "<text:list-item>${item.blocks.joinToString("") { block(it, imagePaths) }}</text:list-item>"
            }}</text:list>"""
        }
        is BlockNode.ListItem -> "<text:list-item>${node.blocks.joinToString("") { block(it, imagePaths) }}</text:list-item>"
        is BlockNode.Table -> """<table:table>${node.rows.joinToString("") { row -> "<table:table-row>${row.cells.joinToString("") { cell -> "<table:table-cell office:value-type=\"string\">${cell.blocks.joinToString("") { block(it, imagePaths) }.ifBlank { "<text:p/>" }}</table:table-cell>" }}</table:table-row>" }}</table:table>"""
        is BlockNode.TableRow, is BlockNode.TableCell -> ""
        is BlockNode.Figure -> "<text:p>${image(node.resourceId, node.altText, imagePaths)}</text:p>"
        is BlockNode.PageBreak -> """<text:p text:style-name="TDocPageBreak"/>"""
        is BlockNode.SectionBreak -> """<text:p text:style-name="TDocPageBreak"/>"""
        is BlockNode.TextBox -> node.blocks.joinToString("") { block(it, imagePaths) }
        is BlockNode.IndexBlock -> "<text:p>${text(node.title ?: node.kind.name)}</text:p>"
        is BlockNode.NoteBody, is BlockNode.RevisionBlock -> when (node) {
            is BlockNode.NoteBody -> node.blocks.joinToString("") { block(it, imagePaths) }
            is BlockNode.RevisionBlock -> node.blocks.joinToString("") { block(it, imagePaths) }
            else -> ""
        }
        is BlockNode.Opaque -> throw IllegalArgumentException("Cannot export opaque block ${node.node.localName}: ${node.node.reason.orEmpty()}")
    }

    private fun inline(node: InlineNode, imagePaths: Map<String, String>, style: TextStyle = TextStyle()): String = when (node) {
        is InlineNode.Text -> {
            val merged = style.mergedWith(node.style)
            val value = text(node.text)
            val styleName = if (merged == TextStyle()) null else textStyles.getOrPut(merged) { "TDocText${textStyles.size + 1}" }
            val styleAttribute = styleName?.let { """ text:style-name="$it"""" }.orEmpty()
            """<text:span$styleAttribute xml:space="preserve">$value</text:span>"""
        }
        is InlineNode.Span -> node.children.joinToString("") { inline(it, imagePaths, style.mergedWith(node.style)) }
        is InlineNode.Break -> "<text:line-break/>"
        is InlineNode.Tab -> "<text:tab/>"
        is InlineNode.NoBreakHyphen -> "&#x2011;"
        is InlineNode.SoftHyphen -> "&#x00AD;"
        is InlineNode.Symbol -> text(node.characterCode.orEmpty())
        is InlineNode.Hyperlink -> """<text:a${node.target?.let { """ xlink:href="${attr(it)}"""" }.orEmpty()}>${node.children.joinToString("") { inline(it, imagePaths, style) }}</text:a>"""
        is InlineNode.Drawing -> image(node.resourceId, node.altText, imagePaths)
        is InlineNode.Field -> node.result.joinToString("") { inline(it, imagePaths, style) }
        is InlineNode.Revision -> node.children.joinToString("") { inline(it, imagePaths, style) }
        is InlineNode.ContentControl -> node.children.joinToString("") { inline(it, imagePaths, style) }
        is InlineNode.Ruby -> node.base.joinToString("") { inline(it, imagePaths, style) }
        is InlineNode.TextBox -> node.blocks.joinToString("") { block(it, imagePaths) }
        is InlineNode.BookmarkBoundary, is InlineNode.NoteReference, is InlineNode.CommentBoundary,
        is InlineNode.CommentReference -> ""
        is InlineNode.Opaque -> throw IllegalArgumentException("Cannot export opaque inline content ${node.node.localName}: ${node.node.reason.orEmpty()}")
    }

    private fun image(resourceId: String?, alt: String?, imagePaths: Map<String, String>): String {
        val path = resourceId?.let(imagePaths::get) ?: return alt?.let(::text).orEmpty()
        return """<draw:frame draw:name="${attr(alt ?: "Image")}" svg:width="1in" svg:height="1in"><draw:image xlink:href="$path" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"/></draw:frame>"""
    }

    private fun text(value: String) = XmlTextSanitizer.escapeText(value)
    private fun attr(value: String) = XmlTextSanitizer.escapeAttribute(value)
    private fun decimal(value: Double) = value.toString().removeSuffix(".0")
}
