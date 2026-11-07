package com.tosin.docprocessor.domain.rendering.table

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [splitForContinuation]: page-split fragments must keep row-aligned
 * resolved presentation so continuation pages paint the correct borders,
 * shading and margins (and repeat the header row like the document rows do).
 */
class TablePresentationSplitTest {

    private fun cell(text: String, shading: String? = null) = BlockNode.TableCell(
        blocks = listOf(BlockNode.Paragraph(inlines = emptyList())),
        style = CellStyle(shading = shading?.let { Shading(fill = it) })
    )

    private fun border(color: String) = Border(style = "single", widthPoints = 1.0, color = color)

    private fun threeRowTable(): BlockNode.Table {
        val grid = CellBorders(
            top = border("000000"), right = border("000000"),
            bottom = border("000000"), left = border("000000")
        )
        return BlockNode.Table(
            style = TableStyle(
                borders = grid,
                insideBorders = CellBorders(
                    top = border("111111"), right = border("111111"),
                    bottom = border("111111"), left = border("111111")
                )
            ),
            rows = listOf(
                BlockNode.TableRow(listOf(cell("h1"), cell("h2")), isHeader = true),
                BlockNode.TableRow(listOf(cell("a", "AAAAAA"), cell("b"))),
                BlockNode.TableRow(listOf(cell("c"), cell("d", "BBBBBB")))
            )
        )
    }

    @Test
    fun `continuation repeats the header row with its own resolved styles`() {
        val presentation = TablePresentationResolver.resolve(threeRowTable())
        assertEquals(3, presentation.rows.size)

        val (first, remaining) = presentation.splitForContinuation(rowsThatFit = 2, repeatHeader = true)

        assertEquals(2, first.rows.size)
        assertNull(first.rows[0].cells[0].shading?.fill)
        assertEquals("AAAAAA", first.rows[1].cells[0].shading?.fill)
        // Continuation = repeated header row + row index 2.
        assertEquals(2, remaining.rows.size)
        assertTrue(remaining.rows[0].isHeader)
        assertNull(remaining.rows[0].cells[0].shading?.fill)
        assertEquals("BBBBBB", remaining.rows[1].cells[1].shading?.fill)
        // Table-level properties are preserved on both fragments.
        assertEquals(presentation.borders, first.borders)
        assertEquals(presentation.borders, remaining.borders)
        assertEquals(presentation.insideBorders, remaining.insideBorders)
    }

    @Test
    fun `continuation without header keeps plain row alignment`() {
        val presentation = TablePresentationResolver.resolve(threeRowTable())

        val (first, remaining) = presentation.splitForContinuation(rowsThatFit = 2, repeatHeader = false)

        assertEquals(2, first.rows.size)
        assertEquals(1, remaining.rows.size)
        assertEquals("BBBBBB", remaining.rows[0].cells[1].shading?.fill)
    }

    @Test
    fun `first fragment keeps resolved row borders after split`() {
        val presentation = TablePresentationResolver.resolve(threeRowTable())
        val (first, _) = presentation.splitForContinuation(rowsThatFit = 1, repeatHeader = true)

        // Row 0 is the header; its bottom edge resolves through insideBorders.
        val headerBottom = first.rows[0].cells[0].borders.bottom
        assertNotNull(headerBottom)
        assertEquals("111111", headerBottom?.color)
    }
}
