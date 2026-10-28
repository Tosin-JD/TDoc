package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.data.parser.internal.models.BookmarkBoundary
import com.tosin.docprocessor.data.parser.internal.models.BookmarkInfo
import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.EdgeInsets
import com.tosin.docprocessor.data.parser.internal.models.EmbeddedObjectInfo
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterContent
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterKind
import com.tosin.docprocessor.data.parser.internal.models.HeaderFooterReference
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.LineSpacingRule
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteKind
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.UnderlineKind
import com.tosin.docprocessor.domain.document.model.style.VerticalAlign
import com.tosin.docprocessor.domain.model.DocumentElement
import java.util.UUID

/**
 * Temporary bridge from the rich model to the legacy flat [DocumentElement]
 * list so the existing renderer keeps working during migration.
 */
class LegacyElementAdapter {

    fun adapt(document: ParsedDocument): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        // Non-content elements (metadata/comments/notes/bookmarks/fields) are excluded from
        // main print flow to avoid polluting page 1. They remain in ParsedDocument for diagnostics.
        output += blocks(document.blocks)
        document.headerFooters.forEach { story ->
            output += DocumentElement.HeaderFooter(
                content = HeaderFooterContent(
                    kind = if (story.kind == com.tosin.docprocessor.domain.document.model.HeaderFooterKind.HEADER) {
                        HeaderFooterKind.HEADER
                    } else HeaderFooterKind.FOOTER,
                    variant = story.variant.name.lowercase(),
                    text = plainText(story.blocks),
                    paragraphCount = story.blocks.size,
                    tableCount = story.blocks.count { it is BlockNode.Table }
                )
            )
        }
        return output
    }

    private fun metadataElement(document: ParsedDocument): DocumentElement {
        val meta = document.metadata
        val attributes = linkedMapOf<String, String>()
        meta.creator?.let { attributes["creator"] = it }
        meta.lastModifiedBy?.let { attributes["lastModifiedBy"] = it }
        meta.created?.let { attributes["created"] = it }
        meta.modified?.let { attributes["modified"] = it }
        meta.application?.let { attributes["Application"] = it }
        attributes.putAll(meta.userDefined)
        attributes.putAll(meta.raw)
        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = when (document.sourceFormat) {
                    SourceFormat.DOCX -> "DOCX Metadata"
                    SourceFormat.ODT -> "ODT Metadata"
                    SourceFormat.UNKNOWN -> "Metadata"
                },
                title = meta.title,
                summary = meta.subject ?: meta.description ?: "Document processed via TDoc",
                attributes = attributes,
                source = "metadata"
            )
        )
    }

    private fun blocks(nodes: List<BlockNode>): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        nodes.forEach { node ->
            when (node) {
                is BlockNode.Heading -> output += DocumentElement.SectionHeader(
                    text = inlineText(node.inlines),
                    level = node.level,
                    style = paragraphStyle(node.style)
                )
                is BlockNode.Paragraph -> output += paragraphElements(node)
                is BlockNode.ListBlock -> node.items.forEach { item ->
                    item.blocks.forEach { child ->
                        if (child is BlockNode.Paragraph) {
                            output += paragraphElements(
                                child.copy(
                                    listMarker = item.marker,
                                    style = child.style
                                )
                            )
                        } else {
                            output += blocks(listOf(child))
                        }
                    }
                }
                is BlockNode.ListItem -> output += blocks(node.blocks)
                is BlockNode.Table -> output += table(node)
                is BlockNode.TableRow, is BlockNode.TableCell -> Unit
                is BlockNode.Figure -> output += figure(node)
                is BlockNode.TextBox -> output += blocks(node.blocks)
                is BlockNode.SectionBreak -> output += DocumentElement.Section(
                    properties = SectionProperties(
                        sectionIndex = node.style.index,
                        source = node.anchor?.part ?: "document",
                        type = node.style.type,
                        pageWidth = node.style.page.widthPoints?.times(20)?.toInt(),
                        pageHeight = node.style.page.heightPoints?.times(20)?.toInt(),
                        margins = EdgeInsets(
                            top = node.style.page.margins.topPoints?.times(20)?.toInt(),
                            right = node.style.page.margins.rightPoints?.times(20)?.toInt(),
                            bottom = node.style.page.margins.bottomPoints?.times(20)?.toInt(),
                            left = node.style.page.margins.leftPoints?.times(20)?.toInt()
                        ),
                        columnCount = node.style.page.columns,
                        pageNumberStart = node.style.pageNumberStart,
                        headerReferences = emptyList<HeaderFooterReference>(),
                        footerReferences = emptyList<HeaderFooterReference>()
                    )
                )
                is BlockNode.PageBreak -> output += DocumentElement.PageBreak()
                is BlockNode.IndexBlock -> output += DocumentElement.Paragraph(
                    spans = listOf(TextSpan(text = node.title ?: node.kind.name))
                )
                is BlockNode.NoteBody -> output += DocumentElement.Note(
                    info = NoteInfo(node.kind.toLegacy(), node.noteId, plainText(node.blocks))
                )
                is BlockNode.RevisionBlock -> output += blocks(node.blocks)
                is BlockNode.Opaque -> output += unsupported(node.node)
            }
        }
        return output
    }

    private fun paragraphElements(node: BlockNode.Paragraph): List<DocumentElement> {
        val pageBreakPositions = findPageBreakPositions(node.inlines)
        if (pageBreakPositions.isEmpty()) {
            val spanResult = spans(node.inlines)
            return listOf(paragraph(node, spanResult)) + spanResult.drawings
        }

        val result = mutableListOf<DocumentElement>()
        val segments = splitInlinesByPageBreaks(node.inlines, pageBreakPositions)
        for ((index, segment) in segments.withIndex()) {
            val spanResult = spans(segment)
            val para = paragraph(node, spanResult)
            // Keep listLabel only on first segment of the split paragraph
            result += if (index == 0) {
                para
            } else if (para is DocumentElement.Paragraph) {
                para.copy(listLabel = null)
            } else {
                para
            }
            result += spanResult.drawings
            if (index < segments.size - 1) {
                result += DocumentElement.PageBreak()
            }
        }
        return result
    }

    private fun findPageBreakPositions(inlines: List<InlineNode>): List<Int> {
        val positions = mutableListOf<Int>()
        inlines.forEachIndexed { idx, inline ->
            if (inline is InlineNode.Break && inline.kind == com.tosin.docprocessor.domain.document.model.BreakKind.PAGE) {
                positions += idx
            }
            if (inline is InlineNode.Span) {
                findPageBreakPositions(inline.children).forEach { _ -> positions += idx }
            }
            if (inline is InlineNode.Hyperlink) {
                findPageBreakPositions(inline.children).forEach { _ -> positions += idx }
            }
        }
        return positions.distinct()
    }

    private fun splitInlinesByPageBreaks(inlines: List<InlineNode>, positions: List<Int>): List<List<InlineNode>> {
        if (positions.isEmpty()) return listOf(inlines)
        val result = mutableListOf<List<InlineNode>>()
        var current = mutableListOf<InlineNode>()
        inlines.forEach { inline ->
            if (inline is InlineNode.Break && inline.kind == com.tosin.docprocessor.domain.document.model.BreakKind.PAGE) {
                result += current.toList()
                current = mutableListOf()
            } else {
                current += inline
            }
        }
        if (current.isNotEmpty() || result.isEmpty()) {
            result += current.toList()
        }
        return result
    }

    private fun paragraphStyle(style: com.tosin.docprocessor.domain.document.model.style.ParagraphStyle): com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle {
        return com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle(
            styleId = style.styleId,
            styleName = style.styleName,
            alignment = style.alignment?.toLegacy() ?: ParagraphAlignment.START,
            indentation = ParagraphIndentation(
                left = style.indentationStartPoints?.times(20)?.toInt()?.takeIf { it > 0 },
                right = style.indentationEndPoints?.times(20)?.toInt()?.takeIf { it > 0 },
                firstLine = style.firstLineIndentPoints?.times(20)?.toInt()?.takeIf { it > 0 },
                hanging = style.hangingIndentPoints?.times(20)?.toInt()?.takeIf { it > 0 }
            ),
            spacing = ParagraphSpacing(
                before = style.spaceBeforePoints?.times(20)?.toInt()?.takeIf { it > 0 },
                after = style.spaceAfterPoints?.times(20)?.toInt()?.takeIf { it > 0 },
                line = style.toLegacySpacingLine()?.takeIf { it > 0 },
                lineRule = style.toLegacySpacingRule()
            ),
            outlineLevel = style.outlineLevel,
            isHeading = false,
            headingLevel = null,
            pageBreakBefore = style.pageBreakBefore == true,
            keepWithNext = style.keepNext == true,
            keepLinesTogether = style.keepLines == true
        )
    }

    private fun paragraph(node: BlockNode.Paragraph, spanResult: SpanResult): DocumentElement {
        val style = node.style
        val listInfo = node.listMarker?.let {
            ListInfo(level = it.level, format = if (it.ordered) "decimal" else "bullet", levelText = it.text)
        }
        val hyperlink = spanResult.hyperlink
        return DocumentElement.Paragraph(
            id = node.anchor?.nativeId ?: UUID.randomUUID().toString(),
            spans = spanResult.spans,
            listLabel = node.listMarker?.text,
            style = paragraphStyle(style),
            hyperlink = hyperlink,
            listInfo = listInfo
        )
    }

    private class SpanResult(
        val spans: List<TextSpan>,
        val hyperlink: HyperlinkInfo?,
        val bookmarks: List<DocumentElement.Bookmark>,
        val fields: List<DocumentElement.Field>,
        val drawings: List<DocumentElement>
    )

    private fun spans(nodes: List<InlineNode>): SpanResult {
        val spans = mutableListOf<TextSpan>()
        val bookmarks = mutableListOf<DocumentElement.Bookmark>()
        val fields = mutableListOf<DocumentElement.Field>()
        val drawings = mutableListOf<DocumentElement>()
        var hyperlink: HyperlinkInfo? = null

        fun walk(inlines: List<InlineNode>, inherited: HyperlinkInfo?, textStyle: com.tosin.docprocessor.domain.document.model.style.TextStyle) {
            inlines.forEach { inline ->
                when (inline) {
                    is InlineNode.Text -> spans += inline.toSpan(textStyle.mergedWith(inline.style), inherited)
                    is InlineNode.Span -> walk(inline.children, inherited, textStyle.mergedWith(inline.style))
                    is InlineNode.Hyperlink -> {
                        val link = HyperlinkInfo(
                            address = inline.target,
                            anchor = inline.anchorName,
                            tooltip = inline.tooltip,
                            docLocation = inline.docLocation
                        )
                        if (hyperlink == null) hyperlink = link
                        walk(inline.children, link, textStyle)
                    }
                    is InlineNode.Break -> when (inline.kind) {
                        com.tosin.docprocessor.domain.document.model.BreakKind.LINE -> spans += TextSpan(
                            text = "\n",
                            color = textStyle.color ?: "000000"
                        )
                        com.tosin.docprocessor.domain.document.model.BreakKind.PAGE,
                        com.tosin.docprocessor.domain.document.model.BreakKind.COLUMN,
                        com.tosin.docprocessor.domain.document.model.BreakKind.SOFT_PAGE -> {
                            // Page/column breaks are handled at paragraph level
                        }
                        else -> Unit
                    }
                    is InlineNode.Tab -> spans += TextSpan(text = "\t")
                    is InlineNode.NoBreakHyphen -> spans += TextSpan(text = "\u2011")
                    is InlineNode.SoftHyphen -> spans += TextSpan(text = "\u00AD")
                    is InlineNode.Symbol -> spans += TextSpan(text = inline.characterCode ?: "")
                    is InlineNode.BookmarkBoundary -> if (inline.isStart && !inline.name.isNullOrBlank()) {
                        bookmarks += DocumentElement.Bookmark(
                            info = BookmarkInfo(
                                id = inline.bookmarkId,
                                name = inline.name,
                                boundary = BookmarkBoundary.START,
                                source = "document"
                            )
                        )
                    }
                    is InlineNode.Field -> fields += DocumentElement.Field(
                        info = FieldInfo(
                            type = inline.kind ?: "field",
                            instruction = inline.instruction,
                            value = inlineText(inline.result),
                            isSimpleField = inline.isSimple
                        )
                    )
                    is InlineNode.Revision -> walk(inline.children, inherited, textStyle)
                    is InlineNode.ContentControl -> walk(inline.children, inherited, textStyle)
                    is InlineNode.Ruby -> {
                        walk(inline.base, inherited, textStyle)
                        walk(inline.rubyText, inherited, textStyle)
                    }
                    is InlineNode.NoteReference -> Unit
                    is InlineNode.CommentBoundary, is InlineNode.CommentReference -> Unit
                    is InlineNode.Drawing -> drawings += drawingElement(inline)
                    is InlineNode.TextBox -> plainText(inline.blocks).takeIf { it.isNotEmpty() }?.let { boxText ->
                        spans += TextSpan(text = boxText, color = textStyle.color ?: "000000")
                    }
                    is InlineNode.Opaque -> drawings += unsupported(inline.node)
                }
            }
        }

        walk(nodes, null, com.tosin.docprocessor.domain.document.model.style.TextStyle())
        return SpanResult(spans, hyperlink, bookmarks, fields, drawings)
    }

    private fun unsupported(node: com.tosin.docprocessor.domain.document.model.OpaqueNode): DocumentElement =
        DocumentElement.EmbeddedObject(
            info = EmbeddedObjectInfo(
                kind = node.localName ?: "unsupported",
                description = node.reason ?: "Unsupported content preserved; not rendered."
            )
        )

    private fun drawingElement(drawing: InlineNode.Drawing): DocumentElement =
        if (!drawing.resourceId.isNullOrBlank()) {
            DocumentElement.Image(
                sourceUri = drawing.resourceId,
                altText = drawing.altText,
                caption = drawing.title
            )
        } else {
            val widthPt = drawing.frameStyle.widthPoints
            val heightPt = drawing.frameStyle.heightPoints
            DocumentElement.Drawing(
                info = com.tosin.docprocessor.data.parser.internal.models.DrawingInfo(
                    kind = "drawing",
                    isInline = drawing.frameStyle.anchor ==
                        com.tosin.docprocessor.domain.document.model.style.FrameAnchor.INLINE,
                    widthEmu = widthPt?.let { (it * 914400f / 72f).toLong() },
                    heightEmu = heightPt?.let { (it * 914400f / 72f).toLong() },
                    altText = drawing.altText,
                    title = drawing.title,
                    wrapStyle = drawing.frameStyle.wrapStyle,
                    rotation = drawing.frameStyle.rotationDegrees?.toInt()
                )
            )
        }

    private fun InlineNode.Text.toSpan(
        style: com.tosin.docprocessor.domain.document.model.style.TextStyle,
        hyperlink: HyperlinkInfo?
    ): TextSpan = TextSpan(
        text = text,
        isBold = style.bold == true,
        isItalic = style.italic == true,
        isUnderline = style.underline != null && style.underline != UnderlineKind.NONE,
        isStrikethrough = style.strike == true,
        isSuperscript = style.verticalAlign == VerticalAlign.SUPERSCRIPT,
        isSubscript = style.verticalAlign == VerticalAlign.SUBSCRIPT,
        isHidden = style.hidden == true,
        fontFamily = style.fontAscii ?: style.fontHAnsi,
        fontSize = style.fontSizePoints?.toFloat(),
        color = style.color?.removePrefix("#") ?: "000000",
        highlightColor = style.highlight,
        characterSpacing = style.letterSpacingPoints?.times(20)?.toInt(),
        language = style.language,
        isSmallCaps = style.smallCaps == true,
        isAllCaps = false,
        hyperlink = hyperlink
    )

    private fun table(node: BlockNode.Table): DocumentElement.Table {
        val rows = node.rows.map { row ->
            row.cells.map { cell -> plainText(cell.blocks) }
        }
        val metadata = TableMetadata(
            styleId = node.style.styleId,
            caption = node.style.caption,
            description = node.style.description,
            rows = node.rows.map { row ->
                row.cells.map { cell ->
                    TableCellMetadata(
                        gridSpan = cell.style.gridSpan,
                        verticalMerge = cell.style.verticalMerge.name.lowercase(),
                        shadingColor = cell.style.shading?.fill
                    )
                }
            }
        )
        val gridColumnWidthsPt = node.gridColumns.mapNotNull { it?.toFloat() }
        return DocumentElement.Table(
            rows = rows,
            hasHeader = node.rows.firstOrNull()?.isHeader == true,
            metadata = metadata,
            gridColumnWidthsPt = gridColumnWidthsPt
        )
    }

    private fun figure(node: BlockNode.Figure): List<DocumentElement> {
        val text = inlineText(node.caption)
        return if (!node.resourceId.isNullOrBlank()) {
            listOf(
                DocumentElement.Image(
                    sourceUri = node.resourceId,
                    altText = node.altText,
                    caption = text.ifBlank { null }
                )
            )
        } else {
            listOf(
                DocumentElement.Drawing(
                    info = com.tosin.docprocessor.data.parser.internal.models.DrawingInfo(
                        kind = "drawing",
                        isInline = node.frameStyle.anchor ==
                            com.tosin.docprocessor.domain.document.model.style.FrameAnchor.INLINE,
                        altText = node.altText,
                        title = node.title
                    )
                )
            )
        }
    }

    private fun plainText(nodes: List<BlockNode>): String = nodes.joinToString("\n") { node ->
        when (node) {
            is BlockNode.Paragraph -> inlineText(node.inlines)
            is BlockNode.Heading -> inlineText(node.inlines)
            is BlockNode.ListBlock -> node.items.joinToString("\n") { item -> plainText(item.blocks) }
            is BlockNode.ListItem -> plainText(node.blocks)
            is BlockNode.Table -> node.rows.joinToString("\n") { row ->
                row.cells.joinToString("\t") { cell -> plainText(cell.blocks) }
            }
            is BlockNode.TextBox -> plainText(node.blocks)
            is BlockNode.RevisionBlock -> plainText(node.blocks)
            is BlockNode.NoteBody -> plainText(node.blocks)
            is BlockNode.Figure -> node.altText ?: inlineText(node.caption)
            else -> ""
        }
    }

    private fun inlineText(nodes: List<InlineNode>): String = buildString {
        nodes.forEach { inline ->
            when (inline) {
                is InlineNode.Text -> append(inline.text)
                is InlineNode.Span -> append(inlineText(inline.children))
                is InlineNode.Hyperlink -> append(inlineText(inline.children))
                is InlineNode.Break -> append('\n')
                is InlineNode.Tab -> append('\t')
                is InlineNode.NoBreakHyphen -> append('\u2011')
                is InlineNode.SoftHyphen -> append('\u00AD')
                is InlineNode.Symbol -> append(inline.characterCode.orEmpty())
                is InlineNode.Field -> append(inlineText(inline.result))
                is InlineNode.Revision -> append(inlineText(inline.children))
                is InlineNode.ContentControl -> append(inlineText(inline.children))
                is InlineNode.Ruby -> append(inlineText(inline.base))
                else -> Unit
            }
        }
    }

    private fun TextAlignment.toLegacy(): ParagraphAlignment = when (this) {
        TextAlignment.START -> ParagraphAlignment.START
        TextAlignment.END -> ParagraphAlignment.END
        TextAlignment.CENTER -> ParagraphAlignment.CENTER
        TextAlignment.JUSTIFIED -> ParagraphAlignment.JUSTIFIED
        TextAlignment.DISTRIBUTED -> ParagraphAlignment.DISTRIBUTED
    }

    private fun com.tosin.docprocessor.domain.document.model.style.ParagraphStyle.toLegacySpacingRule(): LineSpacingRule? {
        if (lineSpacing == null) return null
        return when (lineSpacingRule?.lowercase()) {
            "auto" -> LineSpacingRule.AUTO
            "atleast" -> LineSpacingRule.AT_LEAST
            else -> LineSpacingRule.EXACT
        }
    }

    private fun com.tosin.docprocessor.domain.document.model.style.ParagraphStyle.toLegacySpacingLine(): Int? {
        val value = lineSpacing ?: return null
        return when (toLegacySpacingRule()) {
            LineSpacingRule.AUTO -> Math.round(value * 240.0).toInt()
            else -> Math.round(value * 20.0).toInt()
        }
    }

    private fun com.tosin.docprocessor.domain.document.model.NoteKind.toLegacy(): NoteKind =
        if (this == com.tosin.docprocessor.domain.document.model.NoteKind.FOOTNOTE) {
            NoteKind.FOOTNOTE
        } else NoteKind.ENDNOTE
}
