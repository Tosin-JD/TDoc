package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.StyleCatalogue
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.TableBandFlags
import com.tosin.docprocessor.domain.document.model.style.TableRegion
import com.tosin.docprocessor.domain.document.model.style.TableRegionStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Resolves `word/styles.xml` into a [StyleCatalogue] with `basedOn` inheritance
 * resolved and cycle-safe. Immutable once built.
 */
class DocxStyleResolver private constructor(
    private val paragraphStyles: Map<String, RawStyle>,
    private val characterStyles: Map<String, RawStyle>,
    private val tableStyles: Map<String, RawStyle>,
    val defaultParagraphStyle: ParagraphStyle,
    val defaultTextStyle: TextStyle
) {

    private val paragraphCache = HashMap<String, ParagraphStyle>()
    private val textCache = HashMap<String, TextStyle>()
    private val tableCache = HashMap<String, TableStyle>()

    fun paragraphStyle(styleId: String?): ParagraphStyle {
        if (styleId == null) return defaultParagraphStyle
        return paragraphCache.getOrPut(styleId) {
            val raw = paragraphStyles[styleId] ?: return@getOrPut defaultParagraphStyle
            val chain = inheritanceChain(raw, paragraphStyles)
            var result = defaultParagraphStyle
            chain.forEach { result = result.mergedWith(it.paragraphStyle) }
            val runStyle = chain.fold(TextStyle()) { acc, style -> acc.mergedWith(style.runStyle) }
            val mergedRun = defaultTextStyle.mergedWith(runStyle)
            result.copy(
                styleId = raw.id,
                styleName = raw.name,
                basedOn = raw.basedOn,
                next = raw.next,
                defaultRunStyle = mergedRun
            )
        }
    }

    fun characterStyle(styleId: String?): TextStyle {
        if (styleId == null) return defaultTextStyle
        return textCache.getOrPut(styleId) {
            val raw = characterStyles[styleId] ?: return@getOrPut defaultTextStyle
            val chain = inheritanceChain(raw, characterStyles)
            chain.fold(defaultTextStyle) { acc, style -> acc.mergedWith(style.runStyle) }
        }
    }

    fun tableStyle(styleId: String?): TableStyle {
        if (styleId == null) return TableStyle()
        return tableCache.getOrPut(styleId) {
            val raw = tableStyles[styleId]
            if (raw == null && isBuiltInTableGrid(styleId)) {
                return@getOrPut defaultTableGridStyle(styleId)
            }
            if (raw == null) return@getOrPut TableStyle()
            val chain = inheritanceChain(raw, tableStyles)
            val folded = chain.fold(TableStyle()) { acc, style -> acc.mergedWith(style.tableStyle) }
            val withDefaults = if (isBuiltInTableGrid(raw.id) || isBuiltInTableGrid(raw.name)) {
                defaultTableGridStyle(raw.id).mergedWith(folded)
            } else {
                folded
            }
            withDefaults.copy(styleId = raw.id, styleName = raw.name)
        }
    }

    private fun isBuiltInTableGrid(idOrName: String?): Boolean {
        if (idOrName == null) return false
        val clean = idOrName.replace(" ", "").lowercase()
        return clean == "tablegrid" || clean.startsWith("tablegrid")
    }

    private fun defaultTableGridStyle(styleId: String): TableStyle {
        val defaultBorder = Border(style = "single", widthPoints = 0.5, color = null)
        val outer = CellBorders(
            top = defaultBorder,
            right = defaultBorder,
            bottom = defaultBorder,
            left = defaultBorder
        )
        val inside = CellBorders(
            top = defaultBorder,
            right = defaultBorder,
            bottom = defaultBorder,
            left = defaultBorder
        )
        return TableStyle(
            styleId = styleId,
            styleName = "Table Grid",
            borders = outer,
            insideBorders = inside
        )
    }

    fun numIdForStyle(styleId: String?): String? =
        styleId?.let { paragraphStyles[it]?.numId }

    fun styleLinks(): Map<String, String> =
        paragraphStyles.values.mapNotNull { style -> style.numId?.let { style.id to it } }.toMap()

    fun catalogue(): StyleCatalogue = StyleCatalogue(
        paragraphStyles = paragraphStyles.mapValues { (id, _) -> paragraphStyle(id) },
        textStyles = characterStyles.mapValues { (id, _) -> characterStyle(id) },
        tableStyles = tableStyles.mapValues { (id, _) -> tableStyle(id) },
        defaultParagraphStyle = defaultParagraphStyle,
        defaultTextStyle = defaultTextStyle
    )

    private fun inheritanceChain(style: RawStyle, pool: Map<String, RawStyle>): List<RawStyle> {
        val chain = mutableListOf<RawStyle>()
        val visited = mutableSetOf<String>()
        var current: RawStyle? = style
        while (current != null && visited.add(current.id)) {
            chain += current
            current = current.basedOn?.let(pool::get)
        }
        return chain.reversed()
    }

    private data class RawStyle(
        val id: String,
        val type: String,
        val name: String?,
        val basedOn: String?,
        val next: String?,
        val isDefault: Boolean,
        val paragraphStyle: ParagraphStyle,
        val runStyle: TextStyle,
        val numId: String?,
        val tableStyle: TableStyle
    )

    companion object {
        fun parse(document: Document?): DocxStyleResolver {
            val empty = DocxStyleResolver(emptyMap(), emptyMap(), emptyMap(), ParagraphStyle(), TextStyle())
            if (document == null) return empty
            val root = document.documentElement ?: return empty

            var defaultParagraph = ParagraphStyle()
            var defaultText = TextStyle()
            root.wFirst("docDefaults")?.let { docDefaults ->
                defaultText = DocxRunProperties.parse(docDefaults.wFirst("rPrDefault")?.wFirst("rPr"))
                defaultParagraph = DocxParagraphProperties.parse(
                    docDefaults.wFirst("pPrDefault")?.wFirst("pPr")
                )
            }

            val paragraphs = linkedMapOf<String, RawStyle>()
            val characters = linkedMapOf<String, RawStyle>()
            val tables = linkedMapOf<String, RawStyle>()

            root.w("style").forEach { style ->
                val id = style.wAttr("styleId") ?: return@forEach
                val type = style.wAttr("type")?.lowercase() ?: "paragraph"
                val raw = RawStyle(
                    id = id,
                    type = type,
                    name = style.wFirst("name")?.wAttr("val"),
                    basedOn = style.wFirst("basedOn")?.wAttr("val"),
                    next = style.wFirst("next")?.wAttr("val"),
                    isDefault = style.wAttr("default") == "1" || style.wAttr("default") == "true",
                    paragraphStyle = DocxParagraphProperties.parse(style.wFirst("pPr")),
                    runStyle = DocxRunProperties.parse(style.wFirst("rPr")),
                    numId = style.wFirst("pPr")?.wFirst("numPr")?.wFirst("numId")?.wAttr("val"),
                    tableStyle = parseTableStyle(style)
                )
                when (type) {
                    "character" -> characters[id] = raw
                    "table" -> tables[id] = raw
                    else -> paragraphs[id] = raw
                }
            }

            val resolver = DocxStyleResolver(paragraphs, characters, tables, defaultParagraph, defaultText)
            // A default style may itself define the document defaults.
            val defaultStyleId = paragraphs.values.firstOrNull { it.isDefault }?.id
            val resolvedDefaults = if (defaultStyleId != null) resolver.paragraphStyle(defaultStyleId) else defaultParagraph
            return DocxStyleResolver(
                paragraphs, characters, tables, resolvedDefaults, defaultText
            )
        }

        private fun parseTableStyle(style: Element): TableStyle {
            val tblPr = style.wFirst("tblPr")
            val regions = linkedMapOf<TableRegion, TableRegionStyle>()
            style.w("tblStylePr").forEach { stylePr ->
                val region = TableRegion.fromOoxml(stylePr.wAttr("type")) ?: return@forEach
                val regionTblPr = stylePr.wFirst("tblPr")
                regions[region] = TableRegionStyle(
                    shading = regionTblPr?.wFirst("shd")?.let {
                        com.tosin.docprocessor.domain.document.model.style.Shading(
                            fill = it.wAttr("fill")?.takeUnless { v -> v.equals("auto", true) },
                            color = it.wAttr("color")?.takeUnless { v -> v.equals("auto", true) }
                        )
                    },
                    borders = DocxBorderProperties.parseCellBorders(regionTblPr?.wFirst("tblBorders")),
                    cellMargins = DocxBorderProperties.parseCellMargins(regionTblPr?.wFirst("tblCellMar")),
                    runStyle = DocxRunProperties.parse(stylePr.wFirst("rPr")).takeIf { it != TextStyle() },
                    paragraphStyle = DocxParagraphProperties.parse(stylePr.wFirst("pPr"))
                        .takeIf { it != ParagraphStyle() }
                )
            }
            return TableStyle(
                styleName = style.wFirst("name")?.wAttr("val"),
                borders = DocxBorderProperties.parseCellBorders(tblPr?.wFirst("tblBorders")),
                insideBorders = DocxBorderProperties.parseInsideBorders(tblPr?.wFirst("tblBorders")),
                cellMargins = DocxBorderProperties.parseCellMargins(tblPr?.wFirst("tblCellMar")),
                banding = TableBandFlags(
                    firstRow = hasStylePr(style, "firstRow"),
                    lastRow = hasStylePr(style, "lastRow"),
                    firstColumn = hasStylePr(style, "firstCol"),
                    lastColumn = hasStylePr(style, "lastCol"),
                    band1 = hasStylePr(style, "band1Horz") || hasStylePr(style, "band1Vert"),
                    band2 = hasStylePr(style, "band2Horz") || hasStylePr(style, "band2Vert")
                ),
                regions = regions,
                shading = tblPr?.wFirst("shd")?.let {
                    com.tosin.docprocessor.domain.document.model.style.Shading(
                        fill = it.wAttr("fill")?.takeUnless { v -> v.equals("auto", true) },
                        color = it.wAttr("color")?.takeUnless { v -> v.equals("auto", true) }
                    )
                }
            )
        }

        private fun hasStylePr(style: Element, type: String): Boolean =
            style.w("tblStylePr").any { it.wAttr("type") == type }
    }
}
