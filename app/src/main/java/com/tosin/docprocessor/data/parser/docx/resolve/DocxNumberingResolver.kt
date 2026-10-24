package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.ListMarkerFormatter
import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListLevel
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Immutable resolver for `word/numbering.xml`. A fresh instance is built per
 * import, so numbering never leaks between documents.
 */
class DocxNumberingResolver private constructor(
    private val abstractNums: Map<String, AbstractNumDef>,
    private val numInstances: Map<String, NumInstanceDef>,
    private val styleLinks: Map<String, String>
) {

    fun resolve(numId: String?, ilvl: Int): ListLevel? {
        val instance = numId?.let(numInstances::get) ?: return null
        val abstract = abstractNums[instance.abstractNumId] ?: return null
        val base = abstract.levels[ilvl] ?: return null
        val override = instance.levelOverrides[ilvl]
        return if (override?.start != null) base.copy(start = override.start) else base
    }

    /** Returns the numId linked to a paragraph style, if the style is a list. */
    fun numIdForStyle(styleId: String?): String? = styleId?.let(styleLinks::get)

    fun isOrdered(numId: String?, ilvl: Int): Boolean {
        val format = resolve(numId, ilvl)?.format?.lowercase()
        return format != null && format != "bullet" && format != "none"
    }

    fun kind(numId: String?, ilvl: Int): ListKind =
        if (isOrdered(numId, ilvl)) ListKind.ORDERED else ListKind.UNORDERED

    /**
     * Renders a best-effort list marker label for the given level.
     *
     * @param number 1-based sequence index of the item within its level; the
     *        level's start value is applied here so callers can pass a plain
     *        per-level counter from [com.tosin.docprocessor.data.parser.common.ListNumberingState].
     */
    fun marker(numId: String?, ilvl: Int, number: Int): String? {
        val level = resolve(numId, ilvl) ?: return null
        if (!isOrdered(numId, ilvl)) {
            return level.bulletChar ?: level.levelText?.takeIf { it.length <= 2 }
        }
        val value = (level.start ?: 1) + (number - 1)
        return ListMarkerFormatter.render(
            template = level.levelText ?: "%${ilvl + 1}.",
            value = value,
            format = ListMarkerFormatter.parseFormat(level.format),
            levelNumber = ilvl + 1
        )
    }

    private data class AbstractNumDef(val id: String, val levels: Map<Int, ListLevel>)
    private data class NumInstanceDef(
        val id: String,
        val abstractNumId: String,
        val levelOverrides: Map<Int, LevelOverride>
    )
    private data class LevelOverride(val start: Int? = null)

    companion object {
        private val BULLET_PATTERN = Regex("""^[•◦▪‣·o\-\*]$""")

        fun parse(document: Document?): DocxNumberingResolver {
            if (document == null) return DocxNumberingResolver(emptyMap(), emptyMap(), emptyMap())
            val root = document.documentElement ?: return DocxNumberingResolver(emptyMap(), emptyMap(), emptyMap())

            val abstractNums = linkedMapOf<String, AbstractNumDef>()
            root.w("abstractNum").forEach { absNum ->
                val id = absNum.wAttr("abstractNumId") ?: return@forEach
                val levels = linkedMapOf<Int, ListLevel>()
                absNum.w("lvl").forEach { lvl ->
                    val ilvl = lvl.wAttr("ilvl")?.toIntOrNull() ?: return@forEach
                    levels[ilvl] = parseLevel(ilvl, lvl)
                }
                abstractNums[id] = AbstractNumDef(id, levels)
            }

            val numInstances = linkedMapOf<String, NumInstanceDef>()
            root.w("num").forEach { num ->
                val id = num.wAttr("numId") ?: return@forEach
                val abstractId = num.wFirst("abstractNumId")?.wAttr("val") ?: return@forEach
                val overrides = linkedMapOf<Int, LevelOverride>()
                num.w("lvlOverride").forEach { override ->
                    val ilvl = override.wAttr("ilvl")?.toIntOrNull() ?: return@forEach
                    val start = override.wFirst("startOverride")?.wAttr("val")?.toIntOrNull()
                    overrides[ilvl] = LevelOverride(start)
                }
                numInstances[id] = NumInstanceDef(id, abstractId, overrides)
            }

            val styleLinks = linkedMapOf<String, String>()
            // Styles with numbering are parsed by the style resolver; here we scan
            // style-based numbering defined directly in numbering.xml is not a thing,
            // so links are supplied by the style resolver via [withStyleLinks].
            return DocxNumberingResolver(abstractNums, numInstances, styleLinks)
        }

        private fun parseLevel(ilvl: Int, lvl: Element): ListLevel {
            val numFmt = lvl.wFirst("numFmt")?.wAttr("val")
            val lvlText = lvl.wFirst("lvlText")?.wAttr("val")
            val start = lvl.wFirst("start")?.wAttr("val")?.toIntOrNull()
            val ind = lvl.wFirst("pPr")?.wFirst("ind")
            val left = ind?.wAttr("start")?.toIntOrNull() ?: ind?.wAttr("left")?.toIntOrNull()
            val bulletFont = lvl.wFirst("rPr")?.firstNs(Ns.W, "rFonts")?.wAttr("ascii")
                ?: lvl.wFirst("rPr")?.wFirst("rFonts")?.wAttr("ascii")
            val bulletChar = lvlText?.takeIf { BULLET_PATTERN.matches(it) }
            return ListLevel(
                level = ilvl,
                format = numFmt,
                levelText = lvlText,
                start = start,
                indentPoints = Units.twipsToPoints(left),
                bulletFont = bulletFont,
                bulletChar = bulletChar
            )
        }
    }

    fun withStyleLinks(links: Map<String, String>): DocxNumberingResolver =
        DocxNumberingResolver(abstractNums, numInstances, links)

    fun asListStyle(styleId: String?, numId: String?): ListStyle {
        val levels = linkedMapOf<Int, ListLevel>()
        if (numId != null) {
            val instance = numInstances[numId]
            val abstract = instance?.let { abstractNums[it.abstractNumId] }
            abstract?.levels?.forEach { (level, value) -> levels[level] = value }
        }
        val kind = if (numId != null) kind(numId, 0) else ListKind.UNORDERED
        return ListStyle(styleId = styleId, levels = levels, kind = kind)
    }
}
