package com.tosin.docprocessor.domain.rendering.table

import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableRegion
import com.tosin.docprocessor.domain.document.model.style.TableRegionStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.domain.document.model.style.VerticalMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TablePresentationResolverTest {

    private fun cell(
        vararg texts: String,
        style: CellStyle = CellStyle()
    ): BlockNode.TableCell = BlockNode.TableCell(
        blocks = texts.map { BlockNode.Paragraph(inlines = emptyList()) },
        style = style
    )

    private fun table(
        rows: List<List<BlockNode.TableCell>>,
        style: TableStyle = TableStyle(),
        gridColumns: List<Double?> = emptyList()
    ) = BlockNode.Table(style = style, gridColumns = gridColumns, rows = rows.map { BlockNode.TableRow(it) })

    private fun border(style: String = "single", width: Double = 1.0, color: String? = "FF0000") =
        Border(style = style, widthPoints = width, color = color)

    @Test
    fun `unstyled table resolves to no paint`() {
        val p = TablePresentationResolver.resolve(table(listOf(listOf(cell("a"), cell("b")))))

        assertEquals(2, p.columnCount)
        assertNull(p.shading)
        p.rows.single().cells.forEach { c ->
            assertTrue(c.paintBorders.top == null)
            assertTrue(c.paintBorders.right == null)
            assertTrue(c.paintBorders.bottom == null)
            assertTrue(c.paintBorders.left == null)
            assertNull(c.shading)
        }
    }

    @Test
    fun `table shading and borders resolve per side`() {
        val style = TableStyle(
            shading = Shading(fill = "D9E2F3"),
            borders = CellBorders(
                top = border(), right = border(), bottom = border(), left = border()
            )
        )
        val p = TablePresentationResolver.resolve(
            table(listOf(listOf(cell("a"), cell("b")), listOf(cell("c"), cell("d"))), style)
        )

        assertEquals("D9E2F3", p.shading?.fill)
        val topLeft = p.rows[0].cells[0]
        // Outer edges of a 2x2 table paint; interior edges come from insideBorders
        // which are absent, so only boundary edges draw.
        assertEquals(border(), topLeft.paintBorders.top)
        assertEquals(border(), topLeft.paintBorders.left)
        assertNull(topLeft.paintBorders.bottom) // interior horizontal edge
        assertNull(topLeft.paintBorders.right)  // interior vertical edge
        val bottomRight = p.rows[1].cells[1]
        assertEquals(border(), bottomRight.paintBorders.bottom)
        assertEquals(border(), bottomRight.paintBorders.right)
    }

    @Test
    fun `inside borders paint interior edges only`() {
        val style = TableStyle(
            borders = CellBorders(top = border(), right = border(), bottom = border(), left = border()),
            insideBorders = CellBorders(
                top = border(color = "00FF00"), right = border(color = "00FF00"),
                bottom = border(color = "00FF00"), left = border(color = "00FF00")
            )
        )
        val p = TablePresentationResolver.resolve(
            table(listOf(listOf(cell("a"), cell("b")), listOf(cell("c"), cell("d"))), style)
        )
        val topLeft = p.rows[0].cells[0]
        assertEquals("FF0000", topLeft.paintBorders.top?.color)
        assertEquals("FF0000", topLeft.paintBorders.left?.color)
        // Interior edges are owned by the neighbouring cell's top/left.
        assertEquals("00FF00", p.rows[1].cells[0].paintBorders.top?.color)
        assertEquals("00FF00", p.rows[0].cells[1].paintBorders.left?.color)
    }

    @Test
    fun `cell shading and borders override table style`() {
        val style = TableStyle(
            shading = Shading(fill = "AAAAAA"),
            borders = CellBorders(top = border(), right = border(), bottom = border(), left = border())
        )
        val direct = border(color = "0000FF")
        val p = TablePresentationResolver.resolve(
            table(
                listOf(listOf(cell("a", style = CellStyle(shading = Shading(fill = "123456"), borders = CellBorders(top = direct))))),
                style
            )
        )
        val c = p.rows[0].cells[0]
        assertEquals("123456", c.shading?.fill)
        assertEquals("0000FF", c.paintBorders.top?.color)
    }

    @Test
    fun `explicit nil border suppresses inherited paint`() {
        val nil = Border(style = "nil", widthPoints = 0.0)
        val style = TableStyle(
            borders = CellBorders(top = border(), right = border(), bottom = border(), left = border())
        )
        val p = TablePresentationResolver.resolve(
            table(
                listOf(listOf(cell("a", style = CellStyle(borders = CellBorders(top = nil, left = nil))))),
                style
            )
        )
        val c = p.rows[0].cells[0]
        assertNull(c.paintBorders.top)
        assertNull(c.paintBorders.left)
        assertEquals(border(), c.paintBorders.bottom)
        assertEquals(border(), c.paintBorders.right)
    }

    @Test
    fun `first row region shading applies only to row zero`() {
        val style = TableStyle(
            regions = mapOf(
                TableRegion.FIRST_ROW to TableRegionStyle(shading = Shading(fill = "EEEEEE"))
            )
        )
        val p = TablePresentationResolver.resolve(
            table(listOf(listOf(cell("h")), listOf(cell("a"))), style)
        )
        assertEquals("EEEEEE", p.rows[0].cells[0].shading?.fill)
        assertNull(p.rows[1].cells[0].shading)
    }

    @Test
    fun `banding regions alternate by row parity`() {
        val style = TableStyle(
            regions = mapOf(
                TableRegion.BAND1_HORIZONTAL to TableRegionStyle(shading = Shading(fill = "111111")),
                TableRegion.BAND2_HORIZONTAL to TableRegionStyle(shading = Shading(fill = "222222"))
            )
        )
        val p = TablePresentationResolver.resolve(
            table(
                listOf(listOf(cell("r0")), listOf(cell("r1")), listOf(cell("r2"))),
                style
            )
        )
        assertEquals("111111", p.rows[0].cells[0].shading?.fill)
        assertEquals("222222", p.rows[1].cells[0].shading?.fill)
        assertEquals("111111", p.rows[2].cells[0].shading?.fill)
    }

    @Test
    fun `cell margins resolve per side over table defaults`() {
        val style = TableStyle(
            cellMargins = CellBorders(
                left = Border(widthPoints = 10.0), right = Border(widthPoints = 10.0)
            )
        )
        val p = TablePresentationResolver.resolve(
            table(
                listOf(listOf(cell("a", style = CellStyle(margins = CellBorders(left = Border(widthPoints = 30.0)))))),
                style
            )
        )
        val c = p.rows[0].cells[0]
        assertEquals(30.0, c.margins.left?.widthPoints)
        assertEquals(10.0, c.margins.right?.widthPoints)
        assertNull(c.margins.top)
    }

    @Test
    fun `vertical merge produces covered continuation cells`() {
        val p = TablePresentationResolver.resolve(
            table(
                listOf(
                    listOf(cell("top", style = CellStyle(verticalMerge = VerticalMerge.RESTART)), cell("x")),
                    listOf(cell("cont", style = CellStyle(verticalMerge = VerticalMerge.CONTINUE)), cell("y"))
                )
            )
        )
        val anchor = p.rows[0].cells[0]
        assertEquals(2, anchor.rowSpan)
        assertTrue(p.rows[1].cells[0].isCovered)
        assertEquals(1, p.rows[1].cells[1].rowSpan)
    }

    @Test
    fun `grid span expands covered columns`() {
        val p = TablePresentationResolver.resolve(
            table(
                listOf(listOf(cell("wide", style = CellStyle(gridSpan = 2)), cell("b"))),
                gridColumns = listOf(50.0, 50.0, 50.0)
            )
        )
        assertEquals(3, p.columnCount)
        val wide = p.rows[0].cells[0]
        assertEquals(2, wide.colSpan)
        assertTrue(p.rows[0].cells[1].isCovered)
        assertTrue(!p.rows[0].cells[2].isCovered)
        assertEquals(1, p.rows[0].cells[2].colSpan)
    }

    @Test
    fun `cell alignment and vertical align are preserved`() {
        val p = TablePresentationResolver.resolve(
            table(
                listOf(
                    listOf(
                        cell(
                            "a",
                            style = CellStyle(verticalAlign = VerticalCellAlign.BOTTOM)
                        )
                    )
                )
            )
        )
        val c = p.rows[0].cells[0]
        assertEquals(VerticalCellAlign.BOTTOM, c.verticalAlign)
    }
}
