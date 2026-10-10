package com.tosin.docprocessor.ui.components.table

import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableUiMapperTest {

    private val mapper = TableUiMapper()

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
}
