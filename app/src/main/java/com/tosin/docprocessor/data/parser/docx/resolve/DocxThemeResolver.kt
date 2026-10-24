package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Resolver for `word/theme/theme1.xml`. Resolves theme font references
 * (`asciiTheme="minorHAnsi"` → the theme's minor latin typeface) and theme
 * color names (`themeColor="accent1"` → a concrete sRGB value). Pure; a fresh
 * instance per import.
 */
class DocxThemeResolver private constructor(
    private val majorFonts: FontScheme,
    private val minorFonts: FontScheme,
    private val palette: Map<String, String>
) {

    private data class FontScheme(val latin: String?, val eastAsian: String?, val complexScript: String?)

    /** Returns the concrete family for a theme font attribute name, or null. */
    fun fontFamily(themeAttribute: String?): String? {
        val scheme = when (themeAttribute) {
            "majorAscii", "majorHAnsi" -> majorFonts
            "majorEastAsia" -> majorFonts
            "majorBidi" -> majorFonts
            "minorAscii", "minorHAnsi" -> minorFonts
            "minorEastAsia" -> minorFonts
            "minorBidi" -> minorFonts
            else -> return null
        }
        return when (themeAttribute) {
            "majorEastAsia", "minorEastAsia" -> scheme.eastAsian ?: scheme.latin
            "majorBidi", "minorBidi" -> scheme.complexScript ?: scheme.latin
            else -> scheme.latin
        }
    }

    /** Returns the concrete hex color for a theme color name, or null. */
    fun color(themeColor: String?, tint: String? = null, shade: String? = null): String? {
        val base = themeColor?.lowercase()?.let(palette::get) ?: return null
        return transformColor(base, tint, shade)
    }

    /** Substitutes theme font names and theme colors on a resolved run style. */
    fun resolve(style: TextStyle): TextStyle {
        var result = style
        listOf(style.fontAscii, style.fontHAnsi, style.fontEastAsia, style.fontComplexScript)
            .distinct()
            .forEach { candidate ->
                fontFamily(candidate)?.let { concrete ->
                    result = result.copy(
                        fontAscii = if (result.fontAscii == candidate) concrete else result.fontAscii,
                        fontHAnsi = if (result.fontHAnsi == candidate) concrete else result.fontHAnsi,
                        fontEastAsia = if (result.fontEastAsia == candidate) concrete else result.fontEastAsia,
                        fontComplexScript = if (result.fontComplexScript == candidate) concrete else result.fontComplexScript
                    )
                }
            }
        if (result.color == null && style.colorTheme != null) {
            color(style.colorTheme, style.colorThemeTint, style.colorThemeShade)
                ?.let { result = result.copy(color = it) }
        }
        return result
    }

    private fun transformColor(hex: String, tint: String?, shade: String?): String? {
        val rgb = hex.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16) ?: return null
        val channels = intArrayOf((rgb shr 16) and 0xff, (rgb shr 8) and 0xff, rgb and 0xff)
        val tintValue = tint?.toIntOrNull(16)?.div(255.0)
        val shadeValue = shade?.toIntOrNull(16)?.div(255.0)
        for (index in channels.indices) {
            val value = when {
                tintValue != null -> channels[index] + (255 - channels[index]) * tintValue
                shadeValue != null -> channels[index] * shadeValue
                else -> channels[index].toDouble()
            }.toInt().coerceIn(0, 255)
            channels[index] = value
        }
        return channels.joinToString("") { "%02X".format(it) }
    }

    companion object {
        fun none(): DocxThemeResolver = DocxThemeResolver(
            FontScheme(null, null, null), FontScheme(null, null, null), emptyMap()
        )

        fun parse(document: Document?): DocxThemeResolver {
            val themeElements = document?.documentElement?.firstNs(Ns.A, "themeElements") ?: return none()
            val fontScheme = themeElements.firstNs(Ns.A, "fontScheme")
            val clrScheme = themeElements.firstNs(Ns.A, "clrScheme")
            return DocxThemeResolver(
                majorFonts = parseFonts(fontScheme?.firstNs(Ns.A, "majorFont")),
                minorFonts = parseFonts(fontScheme?.firstNs(Ns.A, "minorFont")),
                palette = parseColors(clrScheme)
            )
        }

        private fun parseFonts(scheme: Element?): FontScheme = FontScheme(
            latin = scheme?.firstNs(Ns.A, "latin")?.getAttribute("typeface")?.takeIf { it.isNotEmpty() },
            eastAsian = scheme?.firstNs(Ns.A, "ea")?.getAttribute("typeface")?.takeIf { it.isNotEmpty() },
            complexScript = scheme?.firstNs(Ns.A, "cs")?.getAttribute("typeface")?.takeIf { it.isNotEmpty() }
        )

        private fun parseColors(scheme: Element?): Map<String, String> {
            if (scheme == null) return emptyMap()
            val palette = linkedMapOf<String, String>()
            val children = scheme.childNodes
            for (index in 0 until children.length) {
                val entry = children.item(index) as? Element ?: continue
                val name = entry.getAttribute("name").takeIf { it.isNotEmpty() }
                    ?: entry.localName?.takeIf { entry.localName != null }
                    ?: continue
                val srgb = entry.firstNs(Ns.A, "srgbClr")?.getAttribute("val")
                val sys = entry.firstNs(Ns.A, "sysClr")?.getAttribute("lastClr")
                (srgb ?: sys)?.takeIf { it.isNotEmpty() }?.let { palette[name.lowercase()] = it }
            }
            return palette
        }
    }
}
