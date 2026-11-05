package com.tosin.docprocessor.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `resolveTextColor lightens dark authored text on a dark page`() {
        val resolved = DocumentTextColorResolver.resolveTextColor("#202020", tokens, darkMode = true)

        assertNotEquals(0xFF202020.toInt(), resolved)
        assertTrue(
            DocumentTextColorResolver.contrastRatio(resolved, tokens.pageBackground) >=
                DocumentTextColorResolver.MIN_CONTRAST
        )
    }

    @Test
    fun `resolveTextColor darkens white authored text on a light page`() {
        val lightTokens = DocumentColorTokens.Light
        val resolved = DocumentTextColorResolver.resolveTextColor("#FFFFFF", lightTokens, darkMode = false)

        assertNotEquals(0xFFFFFFFF.toInt(), resolved)
        assertTrue(
            DocumentTextColorResolver.contrastRatio(resolved, lightTokens.pageBackground) >=
                DocumentTextColorResolver.MIN_CONTRAST
        )
    }

    @Test
    fun `resolveTextColor leaves readable dark text untouched on a light page`() {
        val lightTokens = DocumentColorTokens.Light
        val resolved = DocumentTextColorResolver.resolveTextColor("#333333", lightTokens, darkMode = false)

        assertEquals(0xFF333333.toInt(), resolved)
    }

    @Test
    fun `resolveTextColor preserves alpha while darkening in light mode`() {
        val lightTokens = DocumentColorTokens.Light
        val resolved = DocumentTextColorResolver.resolveTextColor("#80FFFFFF", lightTokens, darkMode = false)

        assertEquals(0x80, (resolved ushr 24) and 0xFF)
        assertTrue(
            DocumentTextColorResolver.contrastRatio(resolved, lightTokens.pageBackground) >=
                DocumentTextColorResolver.MIN_CONTRAST
        )
    }

    @Test
    fun `resolveTextColor leaves readable colours untouched in both modes`() {
        val lightTokens = DocumentColorTokens.Light

        assertEquals(
            0xFF006400.toInt(),
            DocumentTextColorResolver.resolveTextColor("#006400", lightTokens, darkMode = false)
        )
        assertEquals(
            0xFF00FF00.toInt(),
            DocumentTextColorResolver.resolveTextColor("#00FF00", tokens, darkMode = true)
        )
    }

    @Test
    fun `resolveTextColor treats transparent authored colour as unset`() {
        val lightTokens = DocumentColorTokens.Light
        val resolved = DocumentTextColorResolver.resolveTextColor("#00FFFFFF", lightTokens, darkMode = false)

        assertEquals(lightTokens.contentText, resolved)
    }
}
