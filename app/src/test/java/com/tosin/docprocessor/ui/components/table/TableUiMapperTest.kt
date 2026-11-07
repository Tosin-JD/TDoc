package com.tosin.docprocessor.ui.components.table

import androidx.compose.ui.graphics.Color
import com.tosin.docprocessor.data.parser.internal.models.RichTableCell
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import com.tosin.docprocessor.data.parser.internal.models.TableMetadata
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.rendering.table.TablePresentationResolver
import com.tosin.docprocessor.ui.components.table.model.CellTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableUiMapperTest {

    private val mapper = TableUiMapper()

    private fun richCell(
        text: String,
        colSpan: Int = 1,
        rowSpan: Int = 1,
        vMerge: String? = null,
        isCovered: Boolean = false,
        shading: String? = null,
        alignment: TextAlignment = TextAlignment.START,
        verticalAlignment: VerticalCellAlign = VerticalCellAlign.TOP,
        borders: CellBorders? = null
    ): RichTableCell = RichTableCell(
        spans = listOf(TextSpan(text = text, color = "112233")),
        metadata = TableCellMetadata(gridSpan = colSpan, verticalMerge = vMerge),
        colSpan = colSpan,
        rowSpan = rowSpan,
        isCovered = isCovered,
        backgroundColor = shading,
        borders = borders,
        verticalAlignment = verticalAlignment,
        horizontalAlignment = alignment
    )

    @Test
    fun `ragged rows are padded to the widest row`() {
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(
                    listOf("a", "b", "c"),
                    listOf("d", "e")
                )
            )
        )

        assertEquals(3, model.columnCount)
        assertTrue(model.rows.all { it.cells.size == 3 })
        assertEquals("", model.rows[1].cells[2].text)
        assertEquals("e", model.rows[1].cells[1].text)
    }

    @Test
    fun `hasHeader marks only the first row`() {
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(
                    listOf("h1", "h2"),
                    listOf("a", "b"),
                    listOf("c", "d")
                ),
                hasHeader = true
            )
        )

        assertTrue(model.rows[0].isHeader)
        assertFalse(model.rows[1].isHeader)
        assertFalse(model.rows[2].isHeader)
    }

    @Test
    fun `empty table yields empty model`() {
        val model = mapper.map(DocumentElement.Table(rows = emptyList()))

        assertTrue(model.isEmpty)
        assertEquals(0, model.columnCount)
        assertTrue(model.columnWeights.isEmpty())
    }

    @Test
    fun `single cell table has one column`() {
        val model = mapper.map(DocumentElement.Table(rows = listOf(listOf("only"))))

        assertEquals(1, model.columnCount)
        assertEquals(1, model.columnWeights.size)
        assertFalse(model.isEmpty)
    }

    @Test
    fun `weights match column count and stay within bounds`() {
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(
                    listOf("short", "a very very long cell value here"),
                    listOf("x", "y")
                )
            )
        )

        assertEquals(model.columnCount, model.columnWeights.size)
        assertTrue(model.columnWeights.all { it in 1f..8f })
        assertTrue(model.columnWeights[1] > model.columnWeights[0])
    }

    @Test
    fun `grid widths win over content heuristics`() {
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(listOf("a", "b")),
                gridColumnWidthsPt = listOf(30f, 90f)
            )
        )

        assertEquals(listOf(30f, 90f), model.columnWeights)
    }

    @Test
    fun `rich row spans produce a dense rectangular grid`() {
        val redBorder = Border(style = "single", widthPoints = 1.0, color = "FF0000")
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(
                    listOf("Header"),
                    listOf("A", "B")
                ),
                hasHeader = true,
                metadata = TableMetadata(
                    shadingColor = "D9E2F3",
                    borders = CellBorders(top = redBorder, bottom = redBorder)
                ),
                gridColumnWidthsPt = listOf(100f, 50f),
                richRows = listOf(
                    listOf(
                        richCell(
                            "Header",
                            colSpan = 2,
                            shading = "D9E2F3",
                            alignment = TextAlignment.CENTER,
                            verticalAlignment = VerticalCellAlign.CENTER
                        )
                    ),
                    listOf(
                        richCell("A"),
                        richCell("B", alignment = TextAlignment.END)
                    )
                )
            )
        )

        assertEquals(2, model.columnCount)
        assertEquals(listOf(100f, 50f), model.columnWeights)
        assertEquals(Color(0xFFD9E2F3.toInt()), model.tableBackground)
        assertEquals("FF0000", model.outerBorders?.top?.color)

        val header = model.rows[0].cells[0]
        assertEquals(2, header.colSpan)
        assertEquals(Color(0xFFD9E2F3.toInt()), header.backgroundColor)
        assertEquals(CellTextAlignment.CENTER, header.alignment)
        assertEquals(VerticalCellAlign.CENTER, header.verticalAlignment)
        assertTrue(header.spans.single().isBold) // header styling is forced bold
        assertTrue(model.rows[0].cells[1].isCovered)

        val bodyRight = model.rows[1].cells[1]
        assertEquals("B", bodyRight.text)
        assertEquals(CellTextAlignment.END, bodyRight.alignment)
        assertFalse(bodyRight.isCovered)
        assertNull(bodyRight.backgroundColor)
    }

    @Test
    fun `vertical merge marks continuation cells as covered`() {
        val model = mapper.map(
            DocumentElement.Table(
                rows = listOf(
                    listOf("anchor", "r1"),
                    listOf("cont", "r2")
                ),
                richRows = listOf(
                    listOf(
                        richCell("anchor", vMerge = "restart"),
                        richCell("r1")
                    ),
                    listOf(
                        richCell("cont", vMerge = "continue", isCovered = true),
                        richCell("r2")
                    )
                )
            )
        )

        assertEquals(2, model.columnCount)
        assertEquals(2, model.rows[0].cells[0].rowSpan)
        assertFalse(model.rows[0].cells[0].isCovered)
        assertTrue(model.rows[1].cells[0].isCovered)
        assertFalse(model.rows[1].cells[1].isCovered)
    }

    @Test
    fun `resolved presentation projects authored width and alignment`() {
        val blockTable = BlockNode.Table(
            style = TableStyle(
                widthPoints = 200.0,
                alignment = TextAlignment.CENTER
            ),
            rows = listOf(
                BlockNode.TableRow(
                    listOf(
                        BlockNode.TableCell(
                            blocks = listOf(BlockNode.Paragraph(inlines = emptyList()))
                        )
                    )
                )
            )
        )
        val presentation = TablePresentationResolver.resolve(blockTable)

        val model = mapper.map(DocumentElement.Table(rows = emptyList(), presentation = presentation))

        assertEquals(200f, model.preferredWidthPt!!, 0.01f)
        assertEquals(CellTextAlignment.CENTER, model.tableAlignment)
    }
}
