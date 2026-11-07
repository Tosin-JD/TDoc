package com.tosin.docprocessor.data.writer.common

import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ListMarker
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.Resource
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle as ExportParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.domain.document.model.style.UnderlineKind
import com.tosin.docprocessor.domain.document.model.style.VerticalAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import java.io.File
import javax.inject.Inject

/** Explicit bridge from the editor's temporary legacy projection to the export model. */
class LegacyDocumentExportAdapter @Inject constructor() {
    fun adapt(elements: List<DocumentElement>, source: ParsedDocument = ParsedDocument()): ParsedDocument {
        val imageIds = sourceImageIds(source.blocks).iterator()
        val resources = source.resources.toMutableMap()
        val blocks = elements.mapNotNull { element ->
            when (element) {
                is DocumentElement.Paragraph -> BlockNode.Paragraph(
                    inlines = element.spans.map(::inline),
                    style = paragraphStyle(element.style),
                    listMarker = element.listInfo?.let {
                        ListMarker(
                            text = element.listLabel ?: it.levelText,
                            level = it.level,
                            ordered = it.format?.contains("bullet", ignoreCase = true) != true,
                            numId = null
                        )
                    }
                )
                is DocumentElement.SectionHeader -> BlockNode.Heading(
                    inlines = listOf(InlineNode.Text(element.text)),
                    level = element.level,
                    style = paragraphStyle(element.style)
                )
                is DocumentElement.Table -> BlockNode.Table(
                    rows = element.rows.map { row ->
                        BlockNode.TableRow(
                            cells = row.map { value ->
                                BlockNode.TableCell(listOf(BlockNode.Paragraph(listOf(InlineNode.Text(value)))))
                            },
                            isHeader = element.hasHeader
                        )
                    }
                )
                is DocumentElement.Image -> {
                    val resourceId = imageIds.takeIf { it.hasNext() }?.next() ?: element.sourceUri
                    if (resourceId == element.sourceUri && !resources.containsKey(resourceId)) {
                        val file = File(element.sourceUri)
                        if (file.isFile) {
                            val bytes = file.readBytes()
                            val mime = mimeType(file.extension)
                            resources[resourceId] = Resource(
                                id = resourceId,
                                logicalUri = file.name,
                                mimeType = mime,
                                contentHash = resourceId,
                                bytes = bytes
                            )
                        }
                    }
                    BlockNode.Figure(resourceId = resourceId, altText = element.altText, caption = element.caption?.let { listOf(InlineNode.Text(it)) }.orEmpty())
                }
                is DocumentElement.PageBreak -> BlockNode.PageBreak()
                is DocumentElement.Section -> source.sections.getOrNull(element.properties.sectionIndex - 1)
                    ?.let { BlockNode.SectionBreak(it) }
                is DocumentElement.HeaderFooter, is DocumentElement.Note, is DocumentElement.Comment,
                is DocumentElement.Bookmark, is DocumentElement.Field, is DocumentElement.Metadata,
                is DocumentElement.Drawing, is DocumentElement.EmbeddedObject -> null
            }
        }
        return source.copy(blocks = blocks, resources = resources)
    }

    private fun inline(span: TextSpan): InlineNode {
        val textStyle = TextStyle(
            fontAscii = span.fontFamily,
            fontHAnsi = span.fontFamily,
            fontSizePoints = span.fontSize?.toDouble(),
            bold = span.isBold,
            italic = span.isItalic,
            underline = if (span.isUnderline) UnderlineKind.SINGLE else UnderlineKind.NONE,
            strike = span.isStrikethrough,
            verticalAlign = when {
                span.isSuperscript -> VerticalAlign.SUPERSCRIPT
                span.isSubscript -> VerticalAlign.SUBSCRIPT
                else -> null
            },
            color = span.color,
            highlight = span.highlightColor,
            caps = span.isAllCaps,
            smallCaps = span.isSmallCaps,
            hidden = span.isHidden,
            language = span.language
        )
        val text = InlineNode.Text(span.text, textStyle)
        val hyperlink = span.hyperlink
        return if (hyperlink == null) text else InlineNode.Hyperlink(
            children = listOf(text),
            target = hyperlink.address,
            anchorName = hyperlink.anchor,
            tooltip = hyperlink.tooltip,
            docLocation = hyperlink.docLocation
        )
    }

    private fun paragraphStyle(style: com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle) =
        ExportParagraphStyle(
            styleId = style.styleId,
            styleName = style.styleName,
            alignment = when (style.alignment) {
                com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.END -> TextAlignment.END
                com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.CENTER -> TextAlignment.CENTER
                com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.JUSTIFIED -> TextAlignment.JUSTIFIED
                com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment.DISTRIBUTED -> TextAlignment.DISTRIBUTED
                else -> TextAlignment.START
            },
            indentationStartPoints = style.indentation.left?.div(20.0),
            indentationEndPoints = style.indentation.right?.div(20.0),
            firstLineIndentPoints = style.indentation.firstLine?.div(20.0),
            hangingIndentPoints = style.indentation.hanging?.div(20.0),
            spaceBeforePoints = style.spacing.before?.div(20.0),
            spaceAfterPoints = style.spacing.after?.div(20.0),
            outlineLevel = style.outlineLevel,
            pageBreakBefore = style.pageBreakBefore,
            keepNext = style.keepWithNext,
            keepLines = style.keepLinesTogether
        )

    private fun sourceImageIds(blocks: List<BlockNode>): List<String> = buildList {
        lateinit var visitInline: (InlineNode) -> Unit
        lateinit var visitBlock: (BlockNode) -> Unit
        visitInline = { node ->
            when (node) {
                is InlineNode.Drawing -> node.resourceId?.let(::add)
                is InlineNode.Span -> node.children.forEach(visitInline)
                is InlineNode.Hyperlink -> node.children.forEach(visitInline)
                is InlineNode.TextBox -> node.blocks.forEach(visitBlock)
                else -> Unit
            }
        }
        visitBlock = { node ->
            when (node) {
                is BlockNode.Figure -> node.resourceId?.let(::add)
                is BlockNode.Paragraph -> node.inlines.forEach(visitInline)
                is BlockNode.Heading -> node.inlines.forEach(visitInline)
                is BlockNode.ListBlock -> node.items.forEach { it.blocks.forEach(visitBlock) }
                is BlockNode.ListItem -> node.blocks.forEach(visitBlock)
                is BlockNode.Table -> node.rows.forEach { row -> row.cells.forEach { it.blocks.forEach(visitBlock) } }
                is BlockNode.TextBox -> node.blocks.forEach(visitBlock)
                else -> Unit
            }
        }
        blocks.forEach(visitBlock)
    }

    private fun mimeType(extension: String): String? = when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        else -> null
    }
}
