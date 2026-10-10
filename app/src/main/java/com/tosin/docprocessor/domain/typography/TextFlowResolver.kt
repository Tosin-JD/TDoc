package com.tosin.docprocessor.domain.typography

import com.tosin.docprocessor.data.parser.internal.models.LineSpacingRule
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing

/**
 * Pure, platform-free typography math shared by the Compose renderer and the
 * Canvas pagination engine.
 *
 * This is the single source of truth for how paragraph line spacing is resolved
 * and for the anti-clipping floor that guarantees a paragraph never renders a
 * line shorter than its tallest run.
 */
class TextFlowResolver {

    fun resolveLineHeight(spacing: ParagraphSpacing): ResolvedLineHeight {
        val line = spacing.line ?: return ResolvedLineHeight.Unspecified
        return when (spacing.lineRule) {
            LineSpacingRule.EXACT, LineSpacingRule.AT_LEAST ->
                ResolvedLineHeight.Exact(twipsToPt(line))
            // AUTO (and the OOXML default when no rule is recorded).
            LineSpacingRule.AUTO, null ->
                ResolvedLineHeight.Multiplier((line / 240f).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER))
        }
    }

    /**
     * Effective line height in points such that a paragraph never clips its
     * largest run. [maxFontSizePt] is the largest run font size and
     * [baseFontSizePt] the paragraph default.
     */
    fun effectiveLineHeightPt(
        spacing: ParagraphSpacing,
        baseFontSizePt: Float,
        maxFontSizePt: Float
    ): Float {
        val resolved = when (val height = resolveLineHeight(spacing)) {
            is ResolvedLineHeight.Multiplier ->
                sizeNaturalPt(maxFontSizePt) * height.multiplier
            is ResolvedLineHeight.Exact -> height.points
            ResolvedLineHeight.Unspecified ->
                maxOf(sizeNaturalPt(baseFontSizePt), sizeNaturalPt(maxFontSizePt)) * DEFAULT_MULTIPLIER
        }
        val clippingFloor = maxOf(maxFontSizePt, baseFontSizePt) * MIN_LINE_RATIO
        return maxOf(resolved, clippingFloor)
    }

    /** Approximate natural line height of a font at [sizePt] (1.2 × size). */
    fun sizeNaturalPt(sizePt: Float): Float = sizePt * NATURAL_RATIO

    private fun twipsToPt(twips: Int): Float = twips * (72f / 1440f)

    companion object {
        const val MIN_MULTIPLIER = 0.5f
        const val MAX_MULTIPLIER = 4f
        const val DEFAULT_MULTIPLIER = 1.2f
        const val NATURAL_RATIO = 1.2f
        const val MIN_LINE_RATIO = 1.25f
    }
}
