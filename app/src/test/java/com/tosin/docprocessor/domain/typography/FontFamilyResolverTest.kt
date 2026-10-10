package com.tosin.docprocessor.domain.typography

import org.junit.Assert.assertEquals
import org.junit.Test

class FontFamilyResolverTest {

    private val resolver = FontFamilyResolver()

    @Test
    fun `times new roman resolves to serif`() {
        assertEquals(FontFamilyKey.SERIF, resolver.resolve("Times New Roman"))
    }

    @Test
    fun `arial resolves to sans serif`() {
        assertEquals(FontFamilyKey.SANS_SERIF, resolver.resolve("Arial"))
    }

    @Test
    fun `consolas resolves to monospace`() {
        assertEquals(FontFamilyKey.MONOSPACE, resolver.resolve("Consolas"))
    }

    @Test
    fun `comic sans resolves to cursive`() {
        assertEquals(FontFamilyKey.CURSIVE, resolver.resolve("Comic Sans MS"))
    }

    @Test
    fun `null resolves to default`() {
        assertEquals(FontFamilyKey.DEFAULT, resolver.resolve(null))
    }

    @Test
    fun `unknown family falls back to sans serif`() {
        assertEquals(FontFamilyKey.SANS_SERIF, resolver.resolve("SomeUnknownBrandFont"))
    }

    @Test
    fun `matching ignores case and punctuation`() {
        assertEquals(FontFamilyKey.SERIF, resolver.resolve("liberation-serif"))
    }
}
