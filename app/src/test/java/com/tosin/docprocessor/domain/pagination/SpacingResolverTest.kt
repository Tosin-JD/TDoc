package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingResolverTest {
    private val resolver = SpacingResolver()

    @Test
    fun `240 twips before spacing equals 12pt`() {
        val resolved = resolver.resolve(ParagraphSpacing(before = 240))
        assertEquals(12f, resolved.beforePt, 0.01f)
    }

    @Test
    fun `null spacing resolves to zero`() {
        val resolved = resolver.resolve(ParagraphSpacing())
        assertEquals(0f, resolved.beforePt, 0.01f)
        assertEquals(0f, resolved.afterPt, 0.01f)
        assertEquals(0f, resolved.lineSpacingPt, 0.01f)
    }
}
