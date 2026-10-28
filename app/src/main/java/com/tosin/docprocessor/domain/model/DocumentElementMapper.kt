package com.tosin.docprocessor.domain.model

import com.tosin.docprocessor.data.parser.internal.models.*
import java.util.UUID

/**
 * Mapping layer between the parser-owned [DocumentElement] sealed class
 * and the immutable [DocumentBlock] sealed interface.
 *
 * The parser produces [DocumentElement]; pagination and rendering consume
 * [DocumentBlock]. This adapter is the only place that knows about both.
 *
 * Why: We keep [DocumentElement] as the raw parser output (for compatibility
 * with parsers and existing code that consumes it), but all business logic
 * (pagination, rendering, caching) uses [DocumentBlock].
 */
object DocumentElementMapper {

    fun toDocumentBlock(element: DocumentElement): DocumentBlock = when (element) {
        is DocumentElement.Paragraph -> {
            val text = element.spans.joinToString("") { it.text }
            DocumentBlock.Paragraph(
                id = element.id,
                text = text,
                spans = element.spans,
                listInfo = element.listInfo,
                hyperlink = element.hyperlink,
                listLabel = element.listLabel,
                style = TextStyleModel(
                    alignment = element.style.alignment,
                    indentation = element.style.indentation,
                    spacing = element.style.spacing,
                    fontFamily = null,
                    fontSize = null,
                    isBold = element.spans.any { it.isBold },
                    isItalic = element.spans.any { it.isItalic },
                    isUnderline = element.spans.any { it.isUnderline },
                    isStrikethrough = element.spans.any { it.isStrikethrough },
                    color = element.spans.firstOrNull { it.color.isNotEmpty() }?.color ?: "000000",
                    lineRule = element.style.spacing.lineRule
                )
            )
        }
        is DocumentElement.Table -> {
            val rows = element.rows.map { row ->
                val cells = row.map { cellText ->
                    DocumentBlock.TableCell(
                        id = UUID.randomUUID().toString(),
                        rich = RichTableCell(
                            spans = emptyList(),
                            metadata = TableCellMetadata()
                        ),
                        metadata = TableCellMetadata()
                    )
                }
                DocumentBlock.TableRow(
                    id = UUID.randomUUID().toString(),
                    cells = cells,
                    heightPoints = null,
                    isHeader = element.hasHeader && row == element.rows.first()
                )
            }
            DocumentBlock.Table(
                id = element.id,
                rows = rows.map { row ->
                    TableRowModel(
                        cells = row.cells.map { cell -> TableCell(rich = cell.rich, metadata = cell.metadata) },
                        heightPoints = row.heightPoints,
                        isHeader = row.isHeader
                    )
                },
                hasHeader = element.hasHeader,
                metadata = element.metadata,
                richRows = element.richRows,
                gridColumnWidthsPt = element.gridColumnWidthsPt
            )
        }
        is DocumentElement.TableRow -> {
            DocumentBlock.TableRow(
                id = element.id,
                cells = element.cells.map { cell ->
                    DocumentBlock.TableCell(
                        id = cell.id,
                        rich = cell.rich,
                        metadata = cell.metadata
                    )
                },
                heightPoints = element.heightPoints?.toDouble(),
                isHeader = element.isHeader
            )
        }
        is DocumentElement.TableCell -> {
            DocumentBlock.TableCell(
                id = element.id,
                rich = element.rich,
                metadata = element.metadata
            )
        }
        is DocumentElement.Image -> {
            DocumentBlock.ImageBlock(
                id = element.id,
                sourceUri = element.sourceUri,
                altText = element.altText,
                caption = element.caption
            )
        }
        is DocumentElement.SectionHeader -> {
            DocumentBlock.SectionHeader(
                id = element.id,
                text = element.text,
                level = element.level,
                style = TextStyleModel(
                    alignment = element.style.alignment,
                    indentation = element.style.indentation,
                    spacing = element.style.spacing,
                    fontFamily = null,
                    fontSize = null,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    isStrikethrough = false,
                    color = "000000",
                    lineRule = element.style.spacing.lineRule
                ),
                headingFontSizePoints = element.headingFontSizePoints
            )
        }
        is DocumentElement.Section -> {
            DocumentBlock.Section(
                id = element.id,
                properties = element.properties
            )
        }
        is DocumentElement.PageBreak -> {
            DocumentBlock.PageBreak(
                id = element.id
            )
        }
        else -> {
            throw NotImplementedError("Mapping for ${element::class.simpleName} not yet implemented")
        }
    }
}

/**
 * Reverse mapping: [DocumentBlock] → [DocumentElement].
 *
 * Used in the UI layer (PrintLayout) to pass immutable document data into
 * the existing pagination engine, which is still coupled to [DocumentElement].
 */
fun DocumentBlock.toDocumentElement(): DocumentElement = when (this) {
    is DocumentBlock.Paragraph -> {
        val text = text
        DocumentElement.Paragraph(
            id = id,
            spans = spans.map { span ->
                com.tosin.docprocessor.data.parser.internal.models.TextSpan(
                    text = span.text,
                    color = span.color,
                    isBold = span.isBold,
                    isItalic = span.isItalic,
                    isUnderline = span.isUnderline,
                    isStrikethrough = span.isStrikethrough
                )
            },
            listLabel = listLabel,
            style = ParagraphStyle(
                alignment = style.alignment,
                indentation = style.indentation,
                spacing = style.spacing
            ),
            hyperlink = hyperlink,
            listInfo = listInfo
        )
    }
    is DocumentBlock.Table -> DocumentElement.Table(
        id = id,
        rows = rows.map { row ->
            row.cells.map { cell ->
                cell.rich.spans.joinToString("") { it.text }
            }
        },
        hasHeader = hasHeader,
        metadata = metadata,
        richRows = richRows,
        gridColumnWidthsPt = gridColumnWidthsPt
    )
    is DocumentBlock.TableRow -> DocumentElement.TableRow(
        id = id,
        cells = cells.map { cell ->
            DocumentElement.TableCell(
                id = cell.id,
                rich = cell.rich,
                metadata = cell.metadata
            )
        },
        heightPoints = heightPoints?.toDouble(),
        isHeader = isHeader
    )
    is DocumentBlock.TableCell -> DocumentElement.TableCell(
        id = id,
        rich = rich,
        metadata = metadata
    )
    is DocumentBlock.ImageBlock -> DocumentElement.Image(
        id = id,
        sourceUri = sourceUri,
        altText = altText,
        caption = caption
    )
    is DocumentBlock.SectionHeader -> DocumentElement.SectionHeader(
        id = id,
        text = text,
        level = level,
        style = ParagraphStyle(
            alignment = style.alignment,
            indentation = style.indentation,
            spacing = style.spacing
        ),
        headingFontSizePoints = headingFontSizePoints
    )
    is DocumentBlock.Section -> DocumentElement.Section(
        id = id,
        properties = properties
    )
    is DocumentBlock.PageBreak -> DocumentElement.PageBreak(
        id = id
    )
}
