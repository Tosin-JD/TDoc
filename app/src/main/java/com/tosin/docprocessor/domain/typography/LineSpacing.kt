package com.tosin.docprocessor.domain.typography

/**
 * A resolved line height, unit-explicit.
 *
 * Documents express line spacing either as a multiple of the natural line
 * height (`auto`) or as an absolute length (`exact` / `atLeast`). Keeping the
 * two meanings apart prevents the classic bug where a unitless multiplier is
 * treated as points (or vice versa).
 */
sealed interface ResolvedLineHeight {
    /** line height = [multiplier] × natural font line height. */
    data class Multiplier(val multiplier: Float) : ResolvedLineHeight

    /** line height = [points] points (absolute). */
    data class Exact(val points: Float) : ResolvedLineHeight

    data object Unspecified : ResolvedLineHeight
}
