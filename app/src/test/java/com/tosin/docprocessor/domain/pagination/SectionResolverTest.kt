package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.SectionProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionResolverTest {
    private val resolver = SectionResolver()

    @Test
    fun `A4 section resolves to approximately 595 x 842 pt`() {
        val section = SectionProperties(
            sectionIndex = 0,
            source = "test",
            pageWidth = 11906,
            pageHeight = 16838
        )
        val dims = resolver.resolve(section)
        assertEquals(595.3f, dims.width, 0.5f)
        assertEquals(841.9f, dims.height, 0.5f)
    }

    @Test
    fun `null margins fall back to 1-inch defaults`() {
        val section = SectionProperties(sectionIndex = 0, source = "test")
        val dims = resolver.resolve(section)
        assertEquals(72f, dims.marginLeft, 0.01f)
    }
}
