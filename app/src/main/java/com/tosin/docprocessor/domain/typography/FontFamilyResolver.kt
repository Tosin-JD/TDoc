package com.tosin.docprocessor.domain.typography

/**
 * Resolves a document font name (from `w:rFonts` / `fo:font-family`) to a
 * [FontFamilyKey]. Matching is case- and punctuation-insensitive and never
 * throws: unknown or non-Latin names fall back to [FontFamilyKey.SANS_SERIF] so
 * the platform font-fallback chain supplies glyphs instead of rendering tofu.
 */
class FontFamilyResolver {

    fun resolve(fontName: String?): FontFamilyKey {
        val key = fontName
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]"), "")
            ?.takeIf { it.isNotEmpty() }
            ?: return FontFamilyKey.DEFAULT
        return SYNONYMS[key] ?: heuristic(key)
    }

    private fun heuristic(key: String): FontFamilyKey = when {
        key.contains("mono") || key.contains("courier") -> FontFamilyKey.MONOSPACE
        key.contains("script") || key.contains("cursive") -> FontFamilyKey.CURSIVE
        key.contains("serif") && !key.contains("sans") -> FontFamilyKey.SERIF
        else -> FontFamilyKey.SANS_SERIF
    }

    private companion object {
        val SYNONYMS: Map<String, FontFamilyKey> = buildMap {
            listOf(
                "calibri", "arial", "helvetica", "segoeui", "roboto", "verdana",
                "tahoma", "lato", "opensans", "trebuchetms", "franklingothic",
                "corbel", "candara", "dengxian", "microsoftyahei", "malgungothic",
                "arialunicode", "dejavusans", "liberationsans"
            ).forEach { put(it, FontFamilyKey.SANS_SERIF) }

            listOf(
                "timesnewroman", "times", "georgia", "garamond", "cambria",
                "bookantiqua", "palatino", "liberationserif", "dejavuserif",
                "constantia", "宋体", "simsun", "simhei"
            ).forEach { put(it, FontFamilyKey.SERIF) }

            listOf(
                "consolas", "courier", "couriernew", "monaco", "menlo",
                "liberationmono", "dejavusansmono", "cascadiacode", "lucidaconsole"
            ).forEach { put(it, FontFamilyKey.MONOSPACE) }

            listOf(
                "comicsansms", "brushscriptmt", "segoescript", "freestylescript",
                "lucidahandwriting"
            ).forEach { put(it, FontFamilyKey.CURSIVE) }
        }
    }
}
