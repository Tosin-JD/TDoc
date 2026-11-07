package com.tosin.docprocessor.core.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure colour helpers for document rendering. No Android or Compose types, so
 * these functions are unit-testable on the JVM and safe to call from `domain`.
 *
 * The resolver guarantees that document content stays visible on the page in
 * both modes: source colours that fail their WCAG contrast target against the
 * *effective local background* (page → table fill → cell fill → highlight)
 * are interpolated toward white on dark pages and toward black on light pages,
 * while readable colours keep their semantics untouched.
 *
 * Contrast targets differ by role:
 * - text: WCAG AA 4.5:1 ([MIN_CONTRAST])
 * - borders: WCAG non-text 3:1 ([BORDER_MIN_CONTRAST])
 * - fills/highlights: only nudged when they would be indistinguishable from
 *   the background ([FILL_MIN_CONTRAST]), preserving authored hue.
 */
object DocumentTextColorResolver {

    /** WCAG AA contrast ratio for normal text. */
    const val MIN_CONTRAST = 4.5f

    /** WCAG non-text contrast target for borders and graphical objects. */
    const val BORDER_MIN_CONTRAST = 3.0f

    /** Minimum distinguishability for painted fills and highlights. */
    const val FILL_MIN_CONTRAST = 1.2f

    /**
     * Parses `#RGB`, `#RRGGBB`, `#AARRGGBB`, bare hex, `rgb()/rgba()`
     * functional notation, or a known colour name (CSS/X11 names plus the
     * OOXML `w:highlight` named values) into an ARGB int.
     *
     * Returns `null` for blank, invalid, `auto` or `transparent` input —
     * those all mean "no authored colour" downstream. Six-digit values are
     * treated as opaque.
     */
    fun parseColorString(source: String?): Int? {
        val raw = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lowered = raw.lowercase()
        if (lowered == "auto" || lowered == "transparent") return null

        RGB_FUNCTION_REGEX.matchEntire(raw)?.let { match ->
            val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val a = match.groupValues.getOrNull(4)?.toIntOrNull()?.coerceIn(0, 255) ?: 255
            return argb(a, r, g, b)
        }

        NAMED_COLORS[lowered]?.let { return parseColorString(it) }

        val hex = raw.removePrefix("#")
        if (hex.any { it.digitToIntOrNull(16) == null }) return null
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                argb(0xFF, r, g, b)
            }
            6 -> argb(0xFF, hexValue(hex, 0), hexValue(hex, 2), hexValue(hex, 4))
            8 -> argb(hexValue(hex, 0), hexValue(hex, 2), hexValue(hex, 4), hexValue(hex, 6))
            else -> null
        }
    }

    /**
     * Canonical `#RRGGBB` form of a source colour, or null when unset/invalid.
     * Useful for parsers that want to normalise ODT/ODF colour tokens once.
     */
    fun normalizeToHex(source: String?): String? {
        val argb = parseColorString(source) ?: return null
        if (((argb ushr 24) and 0xFF) == 0) return null
        return "#%06X".format(argb and 0xFFFFFF)
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
     * Resolves a source colour for display against [backgroundArgb] (defaults
     * to the page background).
     *
     * Unset, invalid, transparent and pure-black values fall back to
     * [DocumentColorTokens.contentText]. Colours that already meet
     * [MIN_CONTRAST] against the background are returned untouched in both
     * modes. Failing colours are lightened on dark pages and darkened on
     * light pages so authored content never disappears.
     */
    fun resolveTextColor(
        source: String?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int {
        val parsed = parseColorString(source) ?: return tokens.contentText
        return resolveTextColorArgb(parsed, tokens, darkMode, backgroundArgb)
    }

    /** ARGB-int variant of [resolveTextColor] for callers that already parsed. */
    fun resolveTextColorArgb(
        source: Int?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int {
        val parsed = source ?: return tokens.contentText
        if (((parsed ushr 24) and 0xFF) == 0) return tokens.contentText
        if ((parsed and 0x00FFFFFF) == 0) return tokens.contentText
        if (contrastRatio(parsed, backgroundArgb) >= MIN_CONTRAST) return parsed
        return if (darkMode) {
            adjustUntilReadable(parsed, backgroundArgb, MIN_CONTRAST, lighten = true)
        } else {
            adjustUntilReadable(parsed, backgroundArgb, MIN_CONTRAST, lighten = false)
        }
    }

    /**
     * Resolves a painted fill (table/cell/paragraph shading, page background)
     * for display. Returns null when the source is unset so callers paint
     * nothing. Authored fills that would be indistinguishable from the
     * background are nudged away from it; everything else keeps its hue.
     */
    fun resolveBackgroundColor(
        source: String?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int? {
        val parsed = parseColorString(source) ?: return null
        return resolveBackgroundColorArgb(parsed, tokens, darkMode, backgroundArgb)
    }

    /** ARGB-int variant of [resolveBackgroundColor]. */
    fun resolveBackgroundColorArgb(
        source: Int?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int? {
        val parsed = source ?: return null
        if (((parsed ushr 24) and 0xFF) == 0) return null
        if (contrastRatio(parsed, backgroundArgb) >= FILL_MIN_CONTRAST) return parsed
        return adjustUntilReadable(parsed, backgroundArgb, FILL_MIN_CONTRAST, lighten = darkMode)
    }

    /**
     * Resolves a border colour. `null`/unset sources resolve to the themed
     * [DocumentColorTokens.tableBorder] so an authored border never vanishes
     * against the page; authored colours failing [BORDER_MIN_CONTRAST]
     * against the background are nudged into the readable range.
     */
    fun resolveBorderColor(
        source: String?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int {
        val parsed = parseColorString(source)
        if (parsed == null || ((parsed ushr 24) and 0xFF) == 0) return tokens.tableBorder
        return resolveBorderColorArgb(parsed, tokens, darkMode, backgroundArgb)
    }

    /** ARGB-int variant of [resolveBorderColor]; null/transparent → [DocumentColorTokens.tableBorder]. */
    fun resolveBorderColorArgb(
        source: Int?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int {
        val parsed = source
        if (parsed == null || ((parsed ushr 24) and 0xFF) == 0) return tokens.tableBorder
        if (contrastRatio(parsed, backgroundArgb) >= BORDER_MIN_CONTRAST) return parsed
        return adjustUntilReadable(parsed, backgroundArgb, BORDER_MIN_CONTRAST, lighten = darkMode)
    }

    /**
     * Resolves a text-highlight/background-marker colour. Returns null when
     * unset so no marker is painted. Markers keep their hue; they are only
     * nudged when they would be nearly identical to the page background.
     */
    fun resolveHighlightColor(
        source: String?,
        tokens: DocumentColorTokens,
        darkMode: Boolean,
        backgroundArgb: Int = tokens.pageBackground
    ): Int? {
        val parsed = parseColorString(source) ?: return null
        if (((parsed ushr 24) and 0xFF) == 0) return null
        if (contrastRatio(parsed, backgroundArgb) >= FILL_MIN_CONTRAST) return parsed
        return adjustUntilReadable(parsed, backgroundArgb, FILL_MIN_CONTRAST, lighten = darkMode)
    }

    /** Concrete replacement for hardcoded black/white plain-text colours. */
    fun resolvePlainTextColor(tokens: DocumentColorTokens, darkMode: Boolean): Int = tokens.contentText

    private fun adjustUntilReadable(
        color: Int,
        background: Int,
        minContrast: Float,
        lighten: Boolean
    ): Int {
        val alpha = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        var t = 0.0
        while (t <= 1.0) {
            val mixed = argb(
                alpha,
                if (lighten) (r + (255 - r) * t).roundToInt() else (r - r * t).roundToInt(),
                if (lighten) (g + (255 - g) * t).roundToInt() else (g - g * t).roundToInt(),
                if (lighten) (b + (255 - b) * t).roundToInt() else (b - b * t).roundToInt()
            )
            if (contrastRatio(mixed, background) >= minContrast) return mixed
            t += 0.02
        }
        return if (lighten) argb(alpha, 255, 255, 255) else argb(alpha, 0, 0, 0)
    }

    private fun channel(argb: Int, shift: Int): Float {
        val value = ((argb ushr shift) and 0xFF) / 255.0
        return if (value <= 0.03928) {
            (value / 12.92).toFloat()
        } else {
            ((value + 0.055) / 1.055).pow(2.4).toFloat()
        }
    }

    private fun hexValue(raw: String, start: Int): Int =
        raw.substring(start, start + 2).toInt(16)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private val RGB_FUNCTION_REGEX = Regex(
        """rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+)\s*)?\)"""
    )

    /**
     * Colour names understood by document formats: the OOXML `w:highlight`
     * vocabulary plus common CSS/X11 names (mirrors the ODF border parser).
     * Values are `#RRGGBB`.
     */
    val NAMED_COLORS: Map<String, String> = mapOf(
        // OOXML w:highlight named values
        "yellow" to "#FFFF00", "green" to "#00FF00", "cyan" to "#00FFFF",
        "magenta" to "#FF00FF", "blue" to "#0000FF", "red" to "#FF0000",
        "darkblue" to "#000080", "darkcyan" to "#008080", "darkgreen" to "#008000",
        "darkmagenta" to "#800080", "darkred" to "#800000", "darkyellow" to "#808000",
        "darkgray" to "#808080", "lightgray" to "#C0C0C0", "black" to "#000000",
        "white" to "#FFFFFF",
        // CSS/X11 names (lowercase keys; lookup lowercases the input)
        "aqua" to "#00FFFF", "fuchsia" to "#FF00FF", "gray" to "#808080",
        "grey" to "#808080", "silver" to "#C0C0C0", "maroon" to "#800000",
        "olive" to "#808000", "lime" to "#00FF00", "teal" to "#008080",
        "navy" to "#000080", "purple" to "#800080", "orange" to "#FFA500",
        "brown" to "#A52A2A", "pink" to "#FFC0CB", "gold" to "#FFD700",
        "beige" to "#F5F5DC", "ivory" to "#FFFFF0", "khaki" to "#F0E68C",
        "lavender" to "#E6E6FA", "coral" to "#FF7F50", "salmon" to "#FA8072",
        "tomato" to "#FF6347", "violet" to "#EE82EE", "indigo" to "#4B0082",
        "turquoise" to "#40E0D0"
    )
}
