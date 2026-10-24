package com.tosin.docprocessor.data.parser.common

/**
 * Format-aware rendering of ordered-list marker labels, shared by the DOCX and
 * ODT mappers. Pure and JVM-testable: no XML, no Android.
 *
 * Templates use `%n` placeholders (1-based level numbers, e.g. `"%1."` for the
 * outermost level), matching both OOXML `w:lvlText` and ODF `style:num-prefix`
 * / `num-suffix` conventions. Only the placeholder of the *own* level is
 * substituted; placeholders of other levels are left verbatim because their
 * values are not known at this call site.
 */
object ListMarkerFormatter {

    enum class NumberFormat {
        DECIMAL, DECIMAL_ZERO, UPPER_LETTER, LOWER_LETTER, UPPER_ROMAN, LOWER_ROMAN, BULLET, NONE
    }

    /** Maximum nesting depth supported by either format (Word allows 9 levels). */
    const val MAX_LEVELS = 9

    /**
     * Maps a format name to a [NumberFormat]. Accepts OOXML `w:numFmt@val`
     * names (`decimal`, `lowerLetter`, …) and ODF `text:num-format` letters
     * (`a`, `A`, `i`, `I`). Unknown or absent values fall back to [DECIMAL],
     * which is the ODF default for a missing `num-format`.
     */
    fun parseFormat(raw: String?): NumberFormat {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return NumberFormat.DECIMAL
        return when (trimmed.lowercase()) {
            "decimal" -> NumberFormat.DECIMAL
            "decimalzero", "decimal-zero" -> NumberFormat.DECIMAL_ZERO
            "upperletter" -> NumberFormat.UPPER_LETTER
            "lowerletter" -> NumberFormat.LOWER_LETTER
            "upperroman" -> NumberFormat.UPPER_ROMAN
            "lowerroman" -> NumberFormat.LOWER_ROMAN
            "bullet" -> NumberFormat.BULLET
            "none" -> NumberFormat.NONE
            // ODF `text:num-format` uses single case-sensitive letters.
            else -> when (trimmed) {
                "a" -> NumberFormat.LOWER_LETTER
                "A" -> NumberFormat.UPPER_LETTER
                "i" -> NumberFormat.LOWER_ROMAN
                "I" -> NumberFormat.UPPER_ROMAN
                else -> NumberFormat.DECIMAL
            }
        }
    }

    /** Renders the numeric/letter body for [value] in [format] (`3` → `"iii"`, `"c"`…). */
    fun formatNumber(value: Int, format: NumberFormat): String {
        val n = value.coerceAtLeast(0)
        return when (format) {
            NumberFormat.DECIMAL -> n.toString()
            NumberFormat.DECIMAL_ZERO -> n.toString().padStart(2, '0')
            NumberFormat.LOWER_LETTER -> toLetters(n, lowercase = true)
            NumberFormat.UPPER_LETTER -> toLetters(n, lowercase = false)
            NumberFormat.LOWER_ROMAN -> toRoman(n).lowercase()
            NumberFormat.UPPER_ROMAN -> toRoman(n)
            NumberFormat.BULLET, NumberFormat.NONE -> n.toString()
        }
    }

    /**
     * Renders the marker label for one list item.
     *
     * @param template level text containing `%n` placeholders (may be null)
     * @param value the 1-based display value for this item (start value already applied)
     * @param format the level's number format
     * @param levelNumber 1-based level number whose placeholder is substituted
     * @return the rendered label, or null when the template is absent and the
     *         format is not substitutable
     */
    fun render(template: String?, value: Int, format: NumberFormat, levelNumber: Int): String? {
        if (format == NumberFormat.NONE) return null
        val body = formatNumber(value, format)
        val effective = template ?: "%$levelNumber."
        val placeholder = "%$levelNumber"
        return if (effective.contains(placeholder)) {
            effective.replace(placeholder, body)
        } else {
            // No own placeholder (e.g. bare bullet text reused for an ordered
            // level): append the number so the marker never renders wrong.
            effective + body
        }.trim()
    }

    /** Bijective base-26 letters: 1 → a/…, 26 → z, 27 → aa (Word behavior). */
    private fun toLetters(value: Int, lowercase: Boolean): String {
        var n = value
        if (n <= 0) return if (lowercase) "a" else "A"
        val base = if (lowercase) 'a' else 'A'
        val builder = StringBuilder()
        while (n > 0) {
            n-- // 1-based within each cycle
            builder.append((base.code + n % 26).toChar())
            n /= 26
        }
        return builder.reverse().toString()
    }

    private val ROMAN_VALUES = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )

    private fun toRoman(value: Int): String {
        var n = value
        if (n <= 0) return ""
        val builder = StringBuilder()
        for ((amount, symbol) in ROMAN_VALUES) {
            while (n >= amount) {
                builder.append(symbol)
                n -= amount
            }
        }
        return builder.toString()
    }
}
