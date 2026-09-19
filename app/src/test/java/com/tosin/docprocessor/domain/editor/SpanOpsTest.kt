package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanOpsTest {

    private fun spans(vararg styles: Pair<String, Boolean>): List<TextSpan> =
        styles.map { (text, bold) -> TextSpan(text = text, isBold = bold) }

    @Test
    fun `spansText joins all text`() {
        val input = listOf(TextSpan("ab"), TextSpan("cd"), TextSpan("ef"))
        assertEquals("abcdef", SpanOps.spansText(input))
    }

    @Test
    fun `collapsed selection applies transform to entire paragraph`() {
        val input = spans("pokemon" to false)
        val result = SpanOps.toggle(input, Selection(3, 3), SpanProperty.ITALIC)
        assertEquals(listOf(TextSpan("pokemon", isItalic = true)), result)
    }

    @Test
    fun `toggle over partial region splits the span`() {
        val input = listOf(TextSpan(text = "Hello world"))
        val result = SpanOps.toggle(input, Selection(0, 5), SpanProperty.BOLD)
        assertEquals(
            listOf(
                TextSpan(text = "Hello", isBold = true),
                TextSpan(text = " world")
            ),
            result
        )
    }

    @Test
    fun `toggle on already formatted region disables property`() {
        val input = listOf(
            TextSpan(text = "Hello", isBold = true),
            TextSpan(text = " world")
        )
        val result = SpanOps.toggle(input, Selection(0, 5), SpanProperty.BOLD)
        assertEquals(listOf(TextSpan(text = "Hello world")), result)
    }

    @Test
    fun `mixed region becomes fully formatted`() {
        val input = listOf(
            TextSpan(text = "a", isBold = true),
            TextSpan(text = "b")
        )
        val result = SpanOps.toggle(input, Selection(0, 2), SpanProperty.BOLD)
        assertEquals(1, result.size)
        assertTrue(result.single().isBold)
        assertEquals("ab", result.single().text)
    }

    @Test
    fun `applyRange across span boundary merges adjacent equal styles`() {
        val input = listOf(
            TextSpan(text = "foo"),
            TextSpan(text = "bar")
        )
        val result = SpanOps.applyRange(input, Selection(0, 6)) { it.copy(isUnderline = true) }
        assertEquals(1, result.size)
        assertEquals("foobar", result.single().text)
        assertTrue(result.single().isUnderline)
    }

    @Test
    fun `reverse selection is normalised`() {
        val input = listOf(TextSpan(text = "abcd"))
        val result = SpanOps.toggle(input, Selection(4, 0), SpanProperty.BOLD)
        assertEquals(
            listOf(TextSpan("abcd", isBold = true)),
            result
        )
    }

    @Test
    fun `isPropertyActive true when caret inside formatted run`() {
        val input = listOf(TextSpan(text = "bold", isBold = true))
        assertTrue(SpanOps.isPropertyActive(input, Selection(2, 2), SpanProperty.BOLD))
    }

    @Test
    fun `isPropertyActive false when caret inside plain run`() {
        val input = listOf(TextSpan(text = "plain"))
        assertFalse(SpanOps.isPropertyActive(input, Selection(0, 0), SpanProperty.BOLD))
    }

    @Test
    fun `isPropertyActive requires every span in selection formatted`() {
        val input = listOf(
            TextSpan(text = "bold", isBold = true),
            TextSpan(text = "plain")
        )
        assertFalse(SpanOps.isPropertyActive(input, Selection(0, 8), SpanProperty.BOLD))
        assertTrue(SpanOps.isPropertyActive(input, Selection(0, 4), SpanProperty.BOLD))
    }

    @Test
    fun `out of range selection leaves spans untouched`() {
        val input = listOf(TextSpan(text = "hi"))
        val result = SpanOps.applyRange(input, Selection(10, 20)) { it.copy(isItalic = true) }
        assertEquals(input, result)
    }
}