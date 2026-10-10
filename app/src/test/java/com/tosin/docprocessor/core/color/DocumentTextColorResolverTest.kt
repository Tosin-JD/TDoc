package com.tosin.docprocessor.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DocumentTextColorResolverTest {

    private val tokens = DocumentColorTokens(
        canvasBackground = 0xFF1E1E1E.toInt(),
        pageBackground = 0xFF2B2B2B.toInt(),
        contentText = 0xFFECECEC.toInt(),
        secondaryText = 0xFFBDBDBD.toInt(),
        tableBackground = 0xFF323232.toInt(),
        tableHeaderBackground = 0xFF3C3C3C.toInt(),
        tableHeaderText = 0xFFECECEC.toInt(),
        tableBorder = 0xFF9A9A9A.toInt(),
        tableCellBorder = 0xFF5A5A5A.toInt(),
        tableZebraBackground = 0xFF2E2E2E.toInt(),
        placeholderBackground = 0xFF3A3A3A.toInt(),
        placeholderBorder = 0xFF9A9A9A.toInt()
    )

    @Test
    fun `resolveTextColor makes black text readable on dark page`() {
        val resolved = DocumentTextColorResolver.resolveTextColor("#000000", tokens, darkMode = true)
        assertEquals(tokens.contentText, resolved)
    }

    @Test
    fun `resolveTextColor keeps readable source color on dark page`() {
        val resolved = DocumentTextColorResolver.resolveTextColor("#00FF00", tokens, darkMode = true)
        assertEquals(0xFF00FF00.toInt(), resolved)
    }

    @Test
    fun `resolveTextColor falls back to content text for invalid source`() {
        val resolved = DocumentTextColorResolver.resolveTextColor("not-a-color", tokens, darkMode = true)
        assertEquals(tokens.contentText, resolved)
    }
}
