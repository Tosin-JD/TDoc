package com.tosin.docprocessor.ui.components.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableColumnSizerTest {

    private val config = TableLayoutConfig(minColumnWidthDp = 100f, maxColumnWidthDp = 400f)

    @Test
    fun `wide screen fills the available width without scrolling`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(1f, 1f),
            availableWidthDp = 700f,
            config = config
        )

        assertFalse(result.requiresScroll)
        assertEquals(700f, result.columnWidthsDp.sum(), 0.5f)
        assertEquals(700f, result.totalWidthDp, 0.5f)
    }

    @Test
    fun `narrow screen requires scroll and respects minimum width`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(1f, 1f, 1f),
            availableWidthDp = 250f,
            config = config
        )

        assertTrue(result.requiresScroll)
        assertTrue(result.columnWidthsDp.all { it >= config.minColumnWidthDp })
        assertEquals(300f, result.totalWidthDp, 0.5f)
    }

    @Test
    fun `heavy column is widest but never exceeds max`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(8f, 1f, 1f),
            availableWidthDp = 1000f,
            config = config
        )

        assertEquals(config.maxColumnWidthDp, result.columnWidthsDp[0], 0.5f)
        assertTrue(result.columnWidthsDp[1] >= config.minColumnWidthDp)
        assertTrue(result.columnWidthsDp[2] >= config.minColumnWidthDp)
        assertFalse(result.requiresScroll)
    }

    @Test
    fun `zero weights do not crash and fall back to uniform`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(0f, 0f),
            availableWidthDp = 500f,
            config = config
        )

        assertEquals(2, result.columnWidthsDp.size)
        assertEquals(250f, result.columnWidthsDp[0], 0.5f)
        assertEquals(250f, result.columnWidthsDp[1], 0.5f)
    }

    @Test
    fun `available width below total minimum scrolls with min widths`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(1f, 1f, 1f, 1f),
            availableWidthDp = 200f,
            config = config
        )

        assertTrue(result.requiresScroll)
        assertTrue(result.columnWidthsDp.all { it == config.minColumnWidthDp })
        assertEquals(400f, result.totalWidthDp, 0.5f)
    }

    @Test
    fun `empty weights yield empty result`() {
        val result = TableColumnSizer.compute(
            columnWeights = emptyList(),
            availableWidthDp = 500f,
            config = config
        )

        assertTrue(result.columnWidthsDp.isEmpty())
        assertFalse(result.requiresScroll)
        assertEquals(0f, result.totalWidthDp, 0.5f)
    }

    @Test
    fun `no width exceeds the maximum cap`() {
        val result = TableColumnSizer.compute(
            columnWeights = listOf(1f, 1f),
            availableWidthDp = 2000f,
            config = config
        )

        assertTrue(result.columnWidthsDp.all { it <= config.maxColumnWidthDp + 0.5f })
    }
}
