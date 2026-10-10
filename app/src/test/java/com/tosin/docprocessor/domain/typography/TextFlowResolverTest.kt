package com.tosin.docprocessor.domain.typography

import com.tosin.docprocessor.data.parser.internal.models.LineSpacingRule
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFlowResolverTest {

    private val resolver = TextFlowResolver()

    @Test
    fun `auto rule resolves to a line multiplier`() {
        val resolved = resolver.resolveLineHeight(
            ParagraphSpacing(line = 360, lineRule = LineSpacingRule.AUTO)
        )
        assertEquals(ResolvedLineHeight.Multiplier(1.5f), resolved)
    }

    @Test
    fun `exact rule resolves to points`() {
        val resolved = resolver.resolveLineHeight(
            ParagraphSpacing(line = 340, lineRule = LineSpacingRule.EXACT)
        )
        assertEquals(ResolvedLineHeight.Exact(17f), resolved)
    }

    @Test
    fun `at least rule resolves to points`() {
        val resolved = resolver.resolveLineHeight(
            ParagraphSpacing(line = 300, lineRule = LineSpacingRule.AT_LEAST)
        )
        assertEquals(ResolvedLineHeight.Exact(15f), resolved)
    }

    @Test
    fun `unset line resolves to unspecified`() {
        assertEquals(ResolvedLineHeight.Unspecified, resolver.resolveLineHeight(ParagraphSpacing()))
    }

    @Test
    fun `auto multiplier is clamped to a sane range`() {
        val resolved = resolver.resolveLineHeight(
            ParagraphSpacing(line = 10_000, lineRule = LineSpacingRule.AUTO)
        ) as ResolvedLineHeight.Multiplier
        assertEquals(TextFlowResolver.MAX_MULTIPLIER, resolved.multiplier, 0.001f)
    }

    @Test
    fun `effective line height never clips the tallest run`() {
        val spacing = ParagraphSpacing(line = 280, lineRule = LineSpacingRule.EXACT)
        val effective = resolver.effectiveLineHeightPt(
            spacing = spacing,
            baseFontSizePt = 11f,
            maxFontSizePt = 16f
        )
        assertTrue(effective >= 16f * TextFlowResolver.MIN_LINE_RATIO)
    }

    @Test
    fun `unspecified spacing uses the default multiplier on the tallest run`() {
        val effective = resolver.effectiveLineHeightPt(
            spacing = ParagraphSpacing(),
            baseFontSizePt = 11f,
            maxFontSizePt = 16f
        )
        assertEquals(16f * TextFlowResolver.NATURAL_RATIO * TextFlowResolver.DEFAULT_MULTIPLIER, effective, 0.001f)
    }
}
