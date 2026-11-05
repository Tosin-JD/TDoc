package com.tosin.docprocessor.core.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure colour helpers for document rendering. No Android or Compose types, so
 * these functions are unit-testable on the JVM and safe to call from `domain`.
 *
 * The resolver guarantees that document text stays visible on the page in both
 * modes: source colours that fail WCAG AA contrast against the page background
 * are lightened toward white on dark pages and darkened toward black on light
 * pages, while readable colours keep their semantics untouched.
 */
object DocumentTextColorResolver {

    /** WCAG AA contrast ratio for normal text. */
    const val MIN_CONTRAST = 4.5f

    /**
     * Parses `#RGB`, `#RRGGBB`, `#AARRGGBB` or bare hex (optional leading `#`,
     * case-insensitive) into an ARGB int. Returns `null` for blank or invalid
     * input. Six-digit values are treated as opaque.
     */
    fun parseColorString(source: String?): Int? {
        val raw = source?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.any { it.digitToIntOrNull(16) == null }) return null
        return when (raw.length) {
            3 -> {
                val r = raw[0].digitToInt(16) * 17
                val g = raw[1].digitToInt(16) * 17
                val b = raw[2].digitToInt(16) * 17
                argb(0xFF, r, g, b)
            }
            6 -> argb(0xFF, hex(raw, 0), hex(raw, 2), hex(raw, 4))
            8 -> argb(hex(raw, 0), hex(raw, 2), hex(raw, 4), hex(raw, 6))
            else -> null
        }
    }

    /** WCAG relative luminance for an ARGB colour. */
    fun relativeLuminance(argb: Int): Float {
        val r = channel(argb, 16)
        val g = channel(argb, 8)
        val b = channel(argb, 0)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** WCAG contrast ratio between two ARGB colours (1.0 .. 21.0). */
    fun contrastRatio(foreground: Int, background: Int): Float {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Resolves a source colour for display.
     *
     * Unset, invalid, transparent and pure-black values fall back to
     * [DocumentColorTokens.contentText]. Colours that already meet
     * [MIN_CONTRAST] against the page are returned untouched in both modes.
     * Failing colours are lightened on dark pages and darkened on light pages
     * so authored content never disappears against the page.
     */
    fun resolveTextColor(source: String?, tokens: DocumentColorTokens, darkMode: Boolean): Int {
        val parsed = parseColorString(source) ?: return tokens.contentText
        if (((parsed ushr 24) and 0xFF) == 0) return tokens.contentText
        if ((parsed and 0x00FFFFFF) == 0) return tokens.contentText
        if (contrastRatio(parsed, tokens.pageBackground) >= MIN_CONTRAST) return parsed
        return if (darkMode) {
            lightenUntilReadable(parsed, tokens.pageBackground)
        } else {
            darkenUntilReadable(parsed, tokens.pageBackground)
        }
    }

    /** Concrete replacement for hardcoded black/white plain-text colours. */
    fun resolvePlainTextColor(tokens: DocumentColorTokens, darkMode: Boolean): Int = tokens.contentText

    private fun lightenUntilReadable(color: Int, background: Int): Int {
        val alpha = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        var t = 0.0
        while (t <= 1.0) {
            val mixed = argb(
                alpha,
                (r + (255 - r) * t).roundToInt(),
                (g + (255 - g) * t).roundToInt(),
                (b + (255 - b) * t).roundToInt()
            )
            if (contrastRatio(mixed, background) >= MIN_CONTRAST) return mixed
            t += 0.02
        }
        return argb(alpha, 255, 255, 255)
    }

    private fun darkenUntilReadable(color: Int, background: Int): Int {
        val alpha = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        var t = 0.0
        while (t <= 1.0) {
            val mixed = argb(
                alpha,
                (r - r * t).roundToInt(),
                (g - g * t).roundToInt(),
                (b - b * t).roundToInt()
            )
            if (contrastRatio(mixed, background) >= MIN_CONTRAST) return mixed
            t += 0.02
        }
        return argb(alpha, 0, 0, 0)
    }

    private fun channel(argb: Int, shift: Int): Float {
        val value = ((argb ushr shift) and 0xFF) / 255.0
        return if (value <= 0.03928) {
            (value / 12.92).toFloat()
        } else {
            ((value + 0.055) / 1.055).pow(2.4).toFloat()
        }
    }

    private fun hex(raw: String, start: Int): Int =
        raw.substring(start, start + 2).toInt(16)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
