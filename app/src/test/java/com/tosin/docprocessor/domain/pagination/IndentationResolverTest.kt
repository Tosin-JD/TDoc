package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import org.junit.Assert.assertEquals
import org.junit.Test

class IndentationResolverTest {
    private val resolver = IndentationResolver()

    @Test
    fun `list level 2 adds 72pt left indent`() {
        val indent = resolver.resolve(
            ParagraphIndentation(),
            ListInfo(level = 2)
        )
        assertEquals(72f, indent.leftPt, 0.01f)
    }

    @Test
    fun `720 twips left indent equals 36pt`() {
        val indent = resolver.resolve(ParagraphIndentation(left = 720))
        assertEquals(36f, indent.leftPt, 0.01f)
    }

    @Test
    fun `firstLine indent converts correctly`() {
        val indent = resolver.resolve(ParagraphIndentation(firstLine = 360))
        assertEquals(18f, indent.firstLinePt, 0.01f)
    }
}
