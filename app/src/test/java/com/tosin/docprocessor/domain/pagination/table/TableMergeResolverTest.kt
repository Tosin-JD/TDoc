package com.tosin.docprocessor.domain.pagination.table

import com.tosin.docprocessor.data.parser.internal.models.RichTableCell
import com.tosin.docprocessor.data.parser.internal.models.TableCellMetadata
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableMergeResolverTest {

    private fun cell(
        text: String,
        colSpan: Int = 1,
        vMerge: String? = null
    ): RichTableCell = RichTableCell(
        spans = listOf(TextSpan(text)),
        metadata = TableCellMetadata(gridSpan = colSpan, verticalMerge = vMerge),
        colSpan = colSpan,
        rowSpan = 1
    )

    @Test
    fun `horizontal merge colSpan is preserved`() {
        val input = listOf(
            listOf(cell("Header spanning 2", colSpan = 2)),
            listOf(cell("Cell 1"), cell("Cell 2"))
        )
        val resolved = TableMergeResolver.resolve(input)

        assertEquals(2, resolved.size)
        assertEquals(2, resolved[0][0].colSpan)
        assertEquals(1, resolved[0][0].rowSpan)
        assertFalse(resolved[0][0].isCovered)

        assertEquals(1, resolved[1][0].colSpan)
        assertEquals(1, resolved[1][1].colSpan)
    }

    @Test
    fun `vertical merge sets rowSpan on anchor and isCovered on continuation`() {
        val input = listOf(
            listOf(cell("Anchor", vMerge = "restart"), cell("Top Right")),
            listOf(cell("Continuation", vMerge = "continue"), cell("Bottom Right"))
        )
        val resolved = TableMergeResolver.resolve(input)

        assertEquals(2, resolved.size)
        // Anchor
        val anchor = resolved[0][0]
        assertEquals("Anchor", anchor.spans.first().text)
        assertEquals(2, anchor.rowSpan)
        assertFalse(anchor.isCovered)

        // Continuation
        val cont = resolved[1][0]
        assertTrue(cont.isCovered)
        assertEquals(1, cont.rowSpan)

        // Other cells unchanged
        assertFalse(resolved[0][1].isCovered)
        assertFalse(resolved[1][1].isCovered)
    }

    @Test
    fun `explicit hMerge continuations collapse into the anchor`() {
        fun hCell(text: String, hMerge: String?) = RichTableCell(
            spans = listOf(TextSpan(text)),
            metadata = TableCellMetadata(horizontalMerge = hMerge),
            colSpan = 1
        )

        val input = listOf(
            listOf(
                hCell("wide", "restart"),
                hCell("ignored 1", "continue"),
                hCell("ignored 2", "continue"),
                hCell("tail", null)
            )
        )
        val resolved = TableMergeResolver.resolve(input)

        assertEquals(1, resolved.size)
        assertEquals(2, resolved[0].size)
        assertEquals(3, resolved[0][0].colSpan)
        assertEquals("wide", resolved[0][0].spans.single().text)
        assertEquals(1, resolved[0][1].colSpan)
        assertEquals("tail", resolved[0][1].spans.single().text)
    }

    @Test
    fun `mixed horizontal and vertical merges resolve independently`() {
        fun hCell(text: String, hMerge: String?, vMerge: String? = null) = RichTableCell(
            spans = listOf(TextSpan(text)),
            metadata = TableCellMetadata(horizontalMerge = hMerge, verticalMerge = vMerge),
            colSpan = 1
        )

        val input = listOf(
            listOf(
                hCell("wide anchor", "restart", vMerge = "restart"),
                hCell("ignored", "continue"),
                hCell("r1", null)
            ),
            listOf(
                hCell("wide cont", "restart", vMerge = "continue"),
                hCell("ignored", "continue"),
                hCell("r2", null)
            )
        )
        val resolved = TableMergeResolver.resolve(input)

        assertEquals(2, resolved.size)
        assertEquals(2, resolved[0][0].colSpan)
        assertEquals(2, resolved[0][0].rowSpan)
        assertTrue(resolved[1][0].isCovered)
        assertEquals(2, resolved[1].size)
        assertEquals(1, resolved[1].last().colSpan)
        assertFalse(resolved[1].last().isCovered)
    }

    @Test
    fun `three-row vertical merge computes rowSpan of 3`() {
        val input = listOf(
            listOf(cell("Anchor", vMerge = "restart"), cell("R1")),
            listOf(cell("Cont 1", vMerge = "continue"), cell("R2")),
            listOf(cell("Cont 2", vMerge = "continue"), cell("R3"))
        )
        val resolved = TableMergeResolver.resolve(input)

        val anchor = resolved[0][0]
        assertEquals(3, anchor.rowSpan)
        assertFalse(anchor.isCovered)

        assertTrue(resolved[1][0].isCovered)
        assertTrue(resolved[2][0].isCovered)
    }
}

