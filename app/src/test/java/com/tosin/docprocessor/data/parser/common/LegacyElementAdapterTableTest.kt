package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.domain.document.model.SourceFormat
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyElementAdapterTableTest {

    private val adapter = LegacyElementAdapter()

    private fun cell(
        text: String,
        style: CellStyle = CellStyle(),
        textStyle: TextStyle = TextStyle()
    ): BlockNode.TableCell = BlockNode.TableCell(
        blocks = listOf(
            BlockNode.Paragraph(
                inlines = listOf(InlineNode.Text(text, textStyle))
            )
        ),
        style = style
    )

    private fun twoParagraphCell(): BlockNode.TableCell = BlockNode.TableCell(
        blocks = listOf(
            BlockNode.Paragraph(listOf(InlineNode.Text("first", TextStyle(bold = true)))),
            BlockNode.Paragraph(listOf(InlineNode.Text("second", TextStyle(italic = true))))
        )
    )

    private fun table(rows: List<BlockNode.TableRow>, style: TableStyle = TableStyle()) =
        BlockNode.Table(style = style, gridColumns = listOf(100.0, 50.0), rows = rows)

    private fun adapt(node: BlockNode): DocumentElement.Table {
        val document = ParsedDocument(blocks = listOf(node), sourceFormat = SourceFormat.DOCX)
        return adapter.adapt(document).filterIsInstance<DocumentElement.Table>().single()
    }

    @Test
    fun `rich rows preserve styled spans and paragraph breaks`() {
        val result = adapt(
            table(
                listOf(
                    BlockNode.TableRow(
                        cells = listOf(
                            twoParagraphCell(),
                            cell("b")
                        )
                    )
                )
            )
        )

        val first = result.richRows.single()[0]
        val nonBlankSpans = first.spans.filter { it.text.isNotBlank() }
        assertEquals(2, nonBlankSpans.size)
        assertTrue(nonBlankSpans[0].isBold)
        assertEquals("first", nonBlankSpans[0].text)
        assertTrue(nonBlankSpans[1].isItalic)
        assertEquals("second", nonBlankSpans[1].text)
    }

    @Test
    fun `cell formatting metadata is transferred to the rich model`() {
        val redBorder = Border(style = "single", widthPoints = 1.0, color = "FF0000")
        val leftMargin = Border(widthPoints = 6.0)
        val tableStyle = TableStyle(
            styleId = "TableGrid",
            alignment = TextAlignment.END,
            borders = CellBorders(top = redBorder, bottom = redBorder),
            cellMargins = CellBorders(left = leftMargin)
        )
        val result = adapt(
            table(
                rows = listOf(
                    BlockNode.TableRow(
                        cells = listOf(
                            cell(
                                "styled",
                                CellStyle(
                                    gridSpan = 2,
                                    shading = Shading(fill = "D9E2F3"),
                                    verticalAlign = VerticalCellAlign.BOTTOM
                                ),
                                TextStyle(
                                    bold = true,
                                    italic = true,
                                    color = "0000FF",
                                    fontAscii = "Arial",
                                    fontSizePoints = 14.0
                                )
                            )
                        ),
                        heightPoints = 24.0
                    )
                ),
                style = tableStyle
            )
        )

        assertEquals(1, result.richRows.single().size)
        val richCell = result.richRows.single().single()
        assertEquals(2, richCell.colSpan)
        assertEquals("D9E2F3", richCell.backgroundColor)
        assertEquals(VerticalCellAlign.BOTTOM, richCell.verticalAlignment)

        val span = richCell.spans.single()
        assertTrue(span.isBold)
        assertTrue(span.isItalic)
        assertEquals("0000FF", span.color)
        assertEquals("Arial", span.fontFamily)
        assertEquals(14f, span.fontSize ?: 0f, 0.01f)

        val metadata = result.metadata
        assertEquals("TableGrid", metadata.styleId)
        assertEquals(TextAlignment.END, metadata.alignment)
        assertEquals("FF0000", metadata.borders.top?.color)
        assertEquals(6.0, metadata.cellMarginsPt?.left?.widthPoints ?: 0.0, 0.01)
        assertEquals(listOf(100f, 50f), result.gridColumnWidthsPt)
        assertEquals(24f, result.rowHeightsPt.single(), 0.01f)
    }

    @Test
    fun `vertical merge is resolved into rowSpan and covered continuations`() {
        val result = adapt(
            table(
                rows = listOf(
                    BlockNode.TableRow(
                        cells = listOf(
                            cell("anchor", CellStyle(verticalMerge = VerticalMerge.RESTART)),
                            cell("r1")
                        )
                    ),
                    BlockNode.TableRow(
                        cells = listOf(
                            cell("cont", CellStyle(verticalMerge = VerticalMerge.CONTINUE)),
                            cell("r2")
                        )
                    )
                )
            )
        )

        val anchor = result.richRows[0][0]
        assertEquals(2, anchor.rowSpan)
        assertFalse(anchor.isCovered)
        assertTrue(result.richRows[1][0].isCovered)
        assertFalse(result.richRows[1][1].isCovered)
        assertNotNull(result.metadata)
    }
}
